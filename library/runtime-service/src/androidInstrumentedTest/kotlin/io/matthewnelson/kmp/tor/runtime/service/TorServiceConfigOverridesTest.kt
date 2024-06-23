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
package io.matthewnelson.kmp.tor.runtime.service

import android.app.Application
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import io.matthewnelson.kmp.tor.runtime.core.ThisBlock
import io.matthewnelson.kmp.tor.runtime.core.apply
import io.matthewnelson.kmp.tor.runtime.service.internal.ColorRes
import io.matthewnelson.kmp.tor.runtime.service.internal.DrawableRes
import io.matthewnelson.kmp.tor.runtime.service.internal.notification.ServiceNotification
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TorServiceConfigOverridesTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()

    private fun config(
        enableForeground: Boolean = true,
        stopServiceOnTaskRemoved: Boolean = true,
        ifForegroundExitProcessOnDestroyWhenTaskRemoved: Boolean = true,
        notificationId: Int = 21,
        channelId: String = "Test Channel ID",
        channelName: String = "Test Channel Name",
        channelDescription: String = "Test Channel Description",
        channelShowBadge: Boolean = false,
        visibility: Int = 0,
        // Resources can all be invalid (i.e. NONE) here b/c
        // they are not being validated, just used as default
        // values when no override is configured.
        defaults: ServiceNotification.Config = ServiceNotification.Config(
            enableActionRestart = false,
            enableActionStop = false,
            colorWhenBootstrappedTrue = ColorRes.NONE,
            colorWhenBootstrappedFalse = ColorRes.NONE,
            iconNetworkEnabled = DrawableRes.NONE,
            iconNetworkDisabled = DrawableRes.NONE,
            iconDataXfer = DrawableRes.NONE,
            iconError = DrawableRes.NONE,
        ),
    ): TorServiceConfig = object : TorServiceConfig(
        enableForeground = enableForeground,
        stopServiceOnTaskRemoved = stopServiceOnTaskRemoved,
        ifForegroundExitProcessOnDestroyWhenTaskRemoved = ifForegroundExitProcessOnDestroyWhenTaskRemoved,
        notificationId = notificationId,
        channelId = channelId,
        channelName = channelName,
        channelDescription = channelDescription,
        channelShowBadge = channelShowBadge,
        visibility = visibility,
        _defaults = defaults,
        Synthetic.INIT
    ) {}

    private val expectedColor = ColorRes.of(android.R.color.holo_purple)
    private val expectedDrawable = DrawableRes.of(android.R.drawable.stat_notify_chat)

    private val defaults = ThisBlock<TorServiceConfig.OverridesBuilder> {
        enableActionRestart = true
        enableActionStop = true

        colorWhenBootstrappedTrue = expectedColor.id
        colorWhenBootstrappedFalse = expectedColor.id

        iconNetworkEnabled = expectedDrawable.id
        iconNetworkDisabled = expectedDrawable.id
        iconDataXfer = expectedDrawable.id
        iconError = expectedDrawable.id
    }

    @Test
    fun givenOverrides_whenTestDefaults_thenAreValid() {
        val config = config()
        val overrides = TorServiceConfig.OverridesBuilder.build(app, config) {
            apply(defaults)
        }

        assertTrue(overrides.enableActionRestart)
        assertTrue(overrides.enableActionStop)
        assertEquals(expectedColor, overrides.colorWhenBootstrappedTrue)
        assertEquals(expectedColor, overrides.colorWhenBootstrappedFalse)
        assertEquals(expectedDrawable, overrides.iconNetworkEnabled)
        assertEquals(expectedDrawable, overrides.iconNetworkDisabled)
        assertEquals(expectedDrawable, overrides.iconDataXfer)
        assertEquals(expectedDrawable, overrides.iconError)
    }

    @Test
    fun givenOverrides_whenNoneModified_thenUsesConfigDefault() {
        val config = config()
        val overrides = TorServiceConfig.OverridesBuilder.build(app, config) {}

        assertEquals(config.defaults(), overrides)
    }

    @Test
    fun givenOverrides_whenEnableForegroundFalse_thenReturnsConfigDefaults() {
        val config = config(enableForeground = false)
        val overrides = TorServiceConfig.OverridesBuilder.build(app, config) {
            apply(defaults)

            // Would throw if enableForeground = true
            // and was checked/validated
            colorWhenBootstrappedTrue = -1
        }

        assertEquals(config.defaults(), overrides)
    }

    @Test
    fun givenOverrides_whenInvalidColorResourceId_thenThrowsException() {
        val config = config()
        assertFailsWith<Resources.NotFoundException> {
            TorServiceConfig.OverridesBuilder.build(app, config) {
                colorWhenBootstrappedTrue = -1
            }
        }
        assertFailsWith<Resources.NotFoundException> {
            TorServiceConfig.OverridesBuilder.build(app, config) {
                colorWhenBootstrappedFalse = -1
            }
        }
    }

    @Test
    fun givenOverrides_whenInvalidDrawableResourceId_thenThrowsException() {
        val config = config()
        assertFailsWith<Resources.NotFoundException> {
            TorServiceConfig.OverridesBuilder.build(app, config) {
                iconNetworkEnabled = -1
            }
        }
        assertFailsWith<Resources.NotFoundException> {
            TorServiceConfig.OverridesBuilder.build(app, config) {
                iconNetworkDisabled = -1
            }
        }
        assertFailsWith<Resources.NotFoundException> {
            TorServiceConfig.OverridesBuilder.build(app, config) {
                iconDataXfer = -1
            }
        }
        assertFailsWith<Resources.NotFoundException> {
            TorServiceConfig.OverridesBuilder.build(app, config) {
                iconError = -1
            }
        }
    }
}
