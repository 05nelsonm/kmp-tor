/*
 * Copyright (c) 2024 Matthew Nelson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
package io.matthewnelson.kmp.tor.runtime.service.notification

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import io.matthewnelson.kmp.tor.runtime.Action
import io.matthewnelson.kmp.tor.runtime.TorState
import io.matthewnelson.kmp.tor.runtime.service.R
import io.matthewnelson.kmp.tor.runtime.service.internal.notification.*
import io.matthewnelson.kmp.tor.runtime.service.internal.notification.content.ContentBootstrap
import io.matthewnelson.kmp.tor.runtime.service.internal.notification.ButtonAction
import io.matthewnelson.kmp.tor.runtime.service.internal.notification.content.ContentAction
import io.matthewnelson.kmp.tor.runtime.service.internal.notification.content.ContentMessage
import io.matthewnelson.kmp.tor.runtime.service.internal.notification.content.ContentNetworkWaiting
import io.matthewnelson.kmp.tor.runtime.service.internal.renderString
import java.util.Locale
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * String resources can be dangerous... This test simply ensures that all
 * string resources load when they are retrieved and do not throw exception
 * due to a bad translation or formatting error. Shipping that would be awful.
 * */
class NotificationStateResourceUnitTest {

    /**
     * List of all the [Locale] to be checked, and a simple test value
     * for that [Locale] to verify that those resources were in fact
     * loaded.
     *
     * The assertion is performed on [R.string.kmp_tor_bootstrapped_format]
     * to verify that the retrieved string contains "Bootstrapped" (or
     * whatever its translated value is for the [Locale]).
     *
     * e.g. (Adding a locale + test value)
     *
     *     Locale.ENGLISH to "Bootstrapped",
     * */
    private val locales = listOf(
        Locale.ENGLISH to "Bootstrapped",
    )

    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun givenLocale_whenRenderContentFromResource_thenIsSuccess() {
        locales.forEach { (locale, expected) ->
            assertTrue(expected.isNotBlank())

            app.setResourceLocale(locale)

            ButtonAction.entries.forEach { action ->
                locale.tryCatch(action) {
                    renderString(action)
                }.let {}
            }

            Action.entries.forEach { action ->
                locale.tryCatch(action) {
                    renderString(ContentAction.of(action))
                }.let {}
            }

            setOf(
                TorState.Daemon.On(5),
                TorState.Daemon.Off,
                TorState.Daemon.Starting,
                TorState.Daemon.Stopping,
            ).forEach { state ->
                locale.tryCatch(state) {
                    renderString(state)
                }.let {}
            }

            locale.tryCatch("Bootstrapped") {
                renderString(ContentBootstrap.of(5))
            }.let { actual ->
                locale.tryCatch("Bootstrapped{contains expected}") {
                    assertTrue(actual.contains(expected))
                }.let {}
            }

            locale.tryCatch("RateLimited") {
                renderString(ContentMessage.NewNym.RateLimited.Seconds.of(5))
            }.let {}
            locale.tryCatch("NewNymSuccess") {
                renderString(ContentMessage.NewNym.Success)
            }.let {}
            locale.tryCatch("NetworkWaiting") {
                renderString(ContentNetworkWaiting)
            }.let {}
        }
    }

    @Throws(AssertionError::class)
    @OptIn(ExperimentalContracts::class)
    private inline fun <T: Any?> Locale.tryCatch(
        failMsg: Any,
        block: Context.() -> T
    ): T {
        contract {
            callsInPlace(block, InvocationKind.EXACTLY_ONCE)
        }

        val msg = "Locale[$this]: $failMsg"

        val result = try {
            block(app)
        } catch (t: Throwable) {
            throw AssertionError(msg, t)
        }

        println("$msg[result=$result]")

        return result
    }

    private fun Application.setResourceLocale(locale: Locale) {
        val new = Configuration(resources.configuration)
        new.setLocale(locale)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(new, resources.displayMetrics)
    }
}
