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
import io.matthewnelson.kmp.tor.runtime.service.internal.notification.AndroidAbstractNotification
import io.matthewnelson.kmp.tor.runtime.service.internal.notification.BootstrappedString
import io.matthewnelson.kmp.tor.runtime.service.internal.notification.ButtonAction
import io.matthewnelson.kmp.tor.runtime.service.internal.notification.NotificationState
import kotlinx.coroutines.GlobalScope
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * String resources can be dangerous... This test simply ensures that all
 * string resources load when they are retrieved and do not throw exception
 * due to a bad translation or formatting error. Shipping that would be awful.
 * */
class AndroidAbstractNotificationResourceTest {

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

    @Suppress("OPT_IN_USAGE")
    private class TestNotification(
        override val context: Context,
    ) : AndroidAbstractNotification(
        serviceScope = GlobalScope,
        isServiceDestroyed = { false },
        init = Synthetic.INIT,
    ) {
        override fun InstanceView.remove() = TODO("Not yet implemented")
        override fun InstanceView.render(old: NotificationState, new: NotificationState) = TODO("Not yet implemented")

        fun string(action: ButtonAction): String = action.provideString()
        fun string(action: Action): String = action.provideString()
        fun string(daemon: TorState.Daemon): String = daemon.provideString()
        fun stringBootstrapped(byte: Byte): BootstrappedString = byte.provideBootstrappedString()
        fun stringRateLimited(seconds: Int): String = seconds.provideNewNymRateLimitedString()
        fun stringNewNymSuccess(): String = provideNewNymSuccessString()
        fun stringNetworkWaiting(): NetworkWaitingString = provideNetworkWaitingString()
    }

    private val instance = TestNotification(app)

    @Test
    fun givenLocale_whenProvideString_thenStringResourceIsAsExpected() {
        locales.forEach { (locale, expected) ->
            assertTrue(expected.isNotBlank())

            app.setResourceLocale(locale)

            ButtonAction.entries.forEach { action ->
                tryCatch(locale, action) { string(action) }
            }
            Action.entries.forEach { action ->
                tryCatch(locale, action) { string(action) }
            }
            setOf(
                TorState.Daemon.On(5),
                TorState.Daemon.Off,
                TorState.Daemon.Starting,
                TorState.Daemon.Stopping,
            ).forEach { state ->
                tryCatch(locale, state) { string(state) }
            }

            tryCatch(locale, "Bootstrapped") {
                stringBootstrapped(5)
            }.toString().let { actual ->
                tryCatch(locale, "Bootstrapped{contains expected}") {
                    assertTrue(actual.contains(expected))
                }
            }

            tryCatch(locale, "RateLimited") { stringRateLimited(10) }
            tryCatch(locale, "NewNymSuccess") { stringNewNymSuccess() }
            tryCatch(locale, "NetworkWaiting") { stringNetworkWaiting() }
        }
    }

    @Throws(AssertionError::class)
    private fun <T: Any?> tryCatch(
        locale: Locale,
        failMsg: Any,
        block: TestNotification.() -> T
    ): T = try {
        block(instance)
    } catch (t: Throwable) {
        val msg = "Locale[$locale]: $failMsg"
        throw AssertionError(msg, t)
    }

    private fun Application.setResourceLocale(locale: Locale) {
        val new = Configuration(resources.configuration)
        new.setLocale(locale)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(new, resources.displayMetrics)
    }
}
