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

import io.matthewnelson.kmp.tor.runtime.service.TorServiceConfig.MetaData.Companion.KEY_ACTION_ENABLE_RESTART
import io.matthewnelson.kmp.tor.runtime.service.TorServiceConfig.MetaData.Companion.KEY_ACTION_ENABLE_STOP
import io.matthewnelson.kmp.tor.runtime.service.TorServiceConfig.MetaData.Companion.KEY_CHANNEL_DESCRIPTION
import io.matthewnelson.kmp.tor.runtime.service.TorServiceConfig.MetaData.Companion.KEY_CHANNEL_ID
import io.matthewnelson.kmp.tor.runtime.service.TorServiceConfig.MetaData.Companion.KEY_CHANNEL_NAME
import io.matthewnelson.kmp.tor.runtime.service.TorServiceConfig.MetaData.Companion.KEY_CHANNEL_SHOW_BADGE
import io.matthewnelson.kmp.tor.runtime.service.TorServiceConfig.MetaData.Companion.KEY_COLOR_WHEN_BOOTSTRAPPED_FALSE
import io.matthewnelson.kmp.tor.runtime.service.TorServiceConfig.MetaData.Companion.KEY_COLOR_WHEN_BOOTSTRAPPED_TRUE
import io.matthewnelson.kmp.tor.runtime.service.TorServiceConfig.MetaData.Companion.KEY_ENABLE_FOREGROUND
import io.matthewnelson.kmp.tor.runtime.service.TorServiceConfig.MetaData.Companion.KEY_ICON_DATA_XFER
import io.matthewnelson.kmp.tor.runtime.service.TorServiceConfig.MetaData.Companion.KEY_ICON_ERROR
import io.matthewnelson.kmp.tor.runtime.service.TorServiceConfig.MetaData.Companion.KEY_ICON_NETWORK_DISABLED
import io.matthewnelson.kmp.tor.runtime.service.TorServiceConfig.MetaData.Companion.KEY_ICON_NETWORK_ENABLED
import io.matthewnelson.kmp.tor.runtime.service.TorServiceConfig.MetaData.Companion.KEY_IF_FOREGROUND_EXIT_PROCESS_ON_DESTROY_WHEN_TASK_REMOVED
import io.matthewnelson.kmp.tor.runtime.service.TorServiceConfig.MetaData.Companion.KEY_NOTIFICATION_ID
import io.matthewnelson.kmp.tor.runtime.service.TorServiceConfig.MetaData.Companion.KEY_STOP_SERVICE_ON_TASK_REMOVED
import io.matthewnelson.kmp.tor.runtime.service.TorServiceConfig.MetaData.Companion.KEY_VISIBILITY
import io.matthewnelson.kmp.tor.runtime.service.TorServiceConfig.MetaData.Companion.enableForeground
import io.matthewnelson.kmp.tor.runtime.service.TorServiceConfig.MetaData.Companion.stopServiceOnTaskRemoved
import io.matthewnelson.kmp.tor.runtime.service.internal.ColorRes
import io.matthewnelson.kmp.tor.runtime.service.internal.DrawableRes
import kotlin.test.*

class TorServiceConfigUnitTest {

    private class TestMetaData(
        var hasForegroundPerms: Boolean = true,
        var colorIsValid: Boolean = true,
        var drawableIsValid: Boolean = true,
    ): TorServiceConfig.MetaData<IllegalStateException>() {

        var invocationColorValid = 0
        var invocationDrawableValid = 0
        var invocationHasPermission = 0
        val booleans = mutableMapOf<String, Boolean>()
        val ints = mutableMapOf<String, Int>()
        val strings = mutableMapOf<String, String>()

        override fun getBoolean(key: String, default: Boolean): Boolean {
            return booleans[key] ?: default
        }

        override fun getIntOrZero(key: String): Int {
            return ints[key] ?: 0
        }

        override fun getString(key: String): String? {
            return strings[key]
        }

        override fun ColorRes.isValid(): Boolean {
            invocationColorValid++
            return colorIsValid
        }

        override fun DrawableRes.isValid(): Boolean {
            invocationDrawableValid++
            return drawableIsValid
        }

        override fun hasForegroundServicePermission(): Boolean {
            invocationHasPermission++
            return hasForegroundPerms
        }

        override fun createException(message: String): IllegalStateException {
            return IllegalStateException(message)
        }
    }

    private val metaData = TestMetaData()

    @Test
    fun givenNullMetaData_whenEnableForeground_thenDefaultsToFalse() {
        val metaData: TorServiceConfig.MetaData<*>? = null
        assertFalse(metaData.enableForeground())
    }

    @Test
    fun givenNullMetaData_whenStopServiceOnTaskRemoved_thenDefaultsToTrue() {
        val metaData: TorServiceConfig.MetaData<*>? = null
        assertTrue(metaData.stopServiceOnTaskRemoved())
    }

    @Test
    fun givenMetaData_whenEnableForegroundNull_thenDefaultsToFalse() {
        assertFalse(metaData.enableForeground())
    }

    @Test
    fun givenMetaData_whenEnableForegroundFalse_thenDoesNotCheckPermissions() {
        metaData.booleans[KEY_ENABLE_FOREGROUND] = false
        assertFalse(metaData.enableForeground())
        assertEquals(0, metaData.invocationHasPermission)
    }

    @Test
    fun givenMetaData_whenEnableForegroundTrue_thenChecksPermissions() {
        metaData.booleans[KEY_ENABLE_FOREGROUND] = true

        metaData.hasForegroundPerms = false
        assertFalse(metaData.enableForeground())
        assertEquals(1, metaData.invocationHasPermission)

        metaData.hasForegroundPerms = true
        assertTrue(metaData.enableForeground())
        assertEquals(2, metaData.invocationHasPermission)
    }

    @Test
    fun givenMetaData_whenStopServiceOnTaskRemovedNull_thenDefaultsToTrue() {
        assertTrue(metaData.stopServiceOnTaskRemoved())
    }

    @Test
    fun givenMetaData_whenStopServiceOnTaskRemovedTrue_thenIsTrue() {
        metaData.booleans[KEY_STOP_SERVICE_ON_TASK_REMOVED] = true
        assertTrue(metaData.stopServiceOnTaskRemoved())
    }

    @Test
    fun givenMetaData_whenStopServiceOnTaskRemovedFalse_thenIsFalse() {
        metaData.booleans[KEY_STOP_SERVICE_ON_TASK_REMOVED] = false
        assertFalse(metaData.stopServiceOnTaskRemoved())
    }

    @Test
    fun givenMetaData_whenIfForegroundExitProcessOnDestroyWhenTaskRemovedNull_thenDefaultsToTrue() {
        assertTrue(metaData.ifForegroundExitProcessOnDestroyWhenTaskRemoved())
    }

    @Test
    fun givenMetaData_whenIfForegroundExitProcessOnDestroyWhenTaskRemovedTrue_thenIsTrue() {
        metaData.booleans[KEY_IF_FOREGROUND_EXIT_PROCESS_ON_DESTROY_WHEN_TASK_REMOVED] = true
        assertTrue(metaData.ifForegroundExitProcessOnDestroyWhenTaskRemoved())
    }

    @Test
    fun givenMetaData_whenIfForegroundExitProcessOnDestroyWhenTaskRemovedFalse_thenIsFalse() {
        metaData.booleans[KEY_IF_FOREGROUND_EXIT_PROCESS_ON_DESTROY_WHEN_TASK_REMOVED] = false
        assertFalse(metaData.ifForegroundExitProcessOnDestroyWhenTaskRemoved())
    }

    @Test
    fun givenMetaData_whenNotificationIdNull_thenThrowsException() {
        assertFailsWith<IllegalStateException> { metaData.notificationId() }
    }

    @Test
    fun givenMetaData_whenNotificationIdIn1to9999_thenReturnsValue() {
        metaData.ints[KEY_NOTIFICATION_ID] = 1
        assertEquals(1, metaData.notificationId())
        metaData.ints[KEY_NOTIFICATION_ID] = 9999
        assertEquals(9999, metaData.notificationId())
    }

    @Test
    fun givenMetaData_whenNotificationIdExceedsRange1to9999_thenThrowsException() {
        metaData.ints[KEY_NOTIFICATION_ID] = 9999 + 1
        assertFailsWith<IllegalStateException> { metaData.notificationId() }
        metaData.ints[KEY_NOTIFICATION_ID] = 0
        assertFailsWith<IllegalStateException> { metaData.notificationId() }
    }

    @Test
    fun givenMetaData_whenChannelIdNotEmpty_thenReturnsValue() {
        val expected = "not empty"
        metaData.strings[KEY_CHANNEL_ID] = expected
        assertEquals(expected, metaData.channelId())
    }

    @Test
    fun givenMetaData_whenChannelIdNullOrEmpty_thenThrowsException() {
        assertFailsWith<IllegalStateException> { metaData.channelId() }
        metaData.strings[KEY_CHANNEL_ID] = ""
        assertFailsWith<IllegalStateException> { metaData.channelId() }
    }

    @Test
    fun givenMetaData_whenChannelNameNotEmpty_thenReturnsValue() {
        val expected = "not empty"
        metaData.strings[KEY_CHANNEL_NAME] = expected
        assertEquals(expected, metaData.channelName())
    }

    @Test
    fun givenMetaData_whenChannelNameNullOrEmpty_thenThrowsException() {
        assertFailsWith<IllegalStateException> { metaData.channelName() }
        metaData.strings[KEY_CHANNEL_NAME] = ""
        assertFailsWith<IllegalStateException> { metaData.channelName() }
    }

    @Test
    fun givenMetaData_whenChannelDescriptionNotEmpty_thenReturnsValue() {
        val expected = "not empty"
        metaData.strings[KEY_CHANNEL_DESCRIPTION] = expected
        assertEquals(expected, metaData.channelDescription())
    }

    @Test
    fun givenMetaData_whenChannelDescriptionNullOrEmpty_thenThrowsException() {
        assertFailsWith<IllegalStateException> { metaData.channelDescription() }
        metaData.strings[KEY_CHANNEL_DESCRIPTION] = ""
        assertFailsWith<IllegalStateException> { metaData.channelDescription() }
    }

    @Test
    fun givenMetaData_whenChannelShowBadgeNull_thenDefaultsToFalse() {
        assertFalse(metaData.channelShowBadge())
    }

    @Test
    fun givenMetaData_whenChannelShoBadgeTrue_thenIsTrue() {
        metaData.booleans[KEY_CHANNEL_SHOW_BADGE] = true
        assertTrue(metaData.channelShowBadge())
    }

    @Test
    fun givenMetaData_whenChannelShoBadgeFalse_thenIsFalse() {
        metaData.booleans[KEY_CHANNEL_SHOW_BADGE] = false
        assertFalse(metaData.channelShowBadge())
    }

    @Test
    fun givenMetaData_whenIconNetworkEnabledNullOrBelow1_thenThrowsException() {
        assertFailsWith<IllegalStateException> { metaData.iconNetworkEnabled() }
        metaData.ints[KEY_ICON_NETWORK_ENABLED] = 0
        assertFailsWith<IllegalStateException> { metaData.iconNetworkEnabled() }
        metaData.ints[KEY_ICON_NETWORK_ENABLED] = -1
        assertFailsWith<IllegalStateException> { metaData.iconNetworkEnabled() }
    }

    @Test
    fun givenMetaData_whenIconNetworkEnabled_thenChecksValid() {
        val expected = DrawableRes.of(1)
        metaData.ints[KEY_ICON_NETWORK_ENABLED] = expected.id
        assertEquals(expected, metaData.iconNetworkEnabled())
        assertEquals(1, metaData.invocationDrawableValid)
        metaData.drawableIsValid = false
        assertFailsWith<IllegalStateException> { metaData.iconNetworkEnabled() }
    }

    @Test
    fun givenMetaData_whenIconNetworkDisabledNullOrBelow1_thenReturnsNull() {
        assertNull(metaData.iconNetworkDisabled())
        metaData.ints[KEY_ICON_NETWORK_DISABLED] = 0
        assertNull(metaData.iconNetworkDisabled())
        metaData.ints[KEY_ICON_NETWORK_DISABLED] = -1
        assertNull(metaData.iconNetworkDisabled())
    }

    @Test
    fun givenMetaData_whenIconNetworkDisabled_thenChecksValid() {
        val expected = DrawableRes.of(1)
        metaData.ints[KEY_ICON_NETWORK_DISABLED] = expected.id
        assertEquals(expected, metaData.iconNetworkDisabled())
        assertEquals(1, metaData.invocationDrawableValid)
        metaData.drawableIsValid = false
        assertFailsWith<IllegalStateException> { metaData.iconNetworkDisabled() }
    }

    @Test
    fun givenMetaData_whenIconErrorNullOrBelow1_thenThrowsException() {
        assertFailsWith<IllegalStateException> { metaData.iconError() }
        metaData.ints[KEY_ICON_ERROR] = 0
        assertFailsWith<IllegalStateException> { metaData.iconError() }
        metaData.ints[KEY_ICON_ERROR] = -1
        assertFailsWith<IllegalStateException> { metaData.iconError() }
    }

    @Test
    fun givenMetaData_whenIconError_thenChecksValid() {
        val expected = DrawableRes.of(1)
        metaData.ints[KEY_ICON_ERROR] = expected.id
        assertEquals(expected, metaData.iconError())
        assertEquals(1, metaData.invocationDrawableValid)
        metaData.drawableIsValid = false
        assertFailsWith<IllegalStateException> { metaData.iconError() }
    }

    @Test
    fun givenMetaData_whenIconDataXferNullOrBelow1_thenReturnsNull() {
        assertNull(metaData.iconDataXfer())
        metaData.ints[KEY_ICON_DATA_XFER] = 0
        assertNull(metaData.iconDataXfer())
        metaData.ints[KEY_ICON_DATA_XFER] = -1
        assertNull(metaData.iconDataXfer())
    }

    @Test
    fun givenMetaData_whenIconDataXfer_thenChecksValid() {
        val expected = DrawableRes.of(1)
        metaData.ints[KEY_ICON_DATA_XFER] = expected.id
        assertEquals(expected, metaData.iconDataXfer())
        assertEquals(1, metaData.invocationDrawableValid)
        metaData.drawableIsValid = false
        assertFailsWith<IllegalStateException> { metaData.iconDataXfer() }
    }

    @Test
    fun givenMetaData_whenColorBootstrappedTrueNullOrBelow1_thenReturnsNull() {
        assertNull(metaData.colorWhenBootstrappedTrue())
        metaData.ints[KEY_COLOR_WHEN_BOOTSTRAPPED_TRUE] = 0
        assertNull(metaData.colorWhenBootstrappedTrue())
        metaData.ints[KEY_COLOR_WHEN_BOOTSTRAPPED_TRUE] = -1
        assertNull(metaData.colorWhenBootstrappedTrue())
    }

    @Test
    fun givenMetaData_whenColorBootstrappedTrue_thenChecksValid() {
        val expected = ColorRes.of(1)
        metaData.ints[KEY_COLOR_WHEN_BOOTSTRAPPED_TRUE] = expected.id
        assertEquals(expected, metaData.colorWhenBootstrappedTrue())
        assertEquals(1, metaData.invocationColorValid)
        metaData.colorIsValid = false
        assertFailsWith<IllegalStateException> { metaData.colorWhenBootstrappedTrue() }
    }

    @Test
    fun givenMetaData_whenColorBootstrappedFalseNullOrBelow1_thenReturnsNull() {
        assertNull(metaData.colorWhenBootstrappedFalse())
        metaData.ints[KEY_COLOR_WHEN_BOOTSTRAPPED_FALSE] = 0
        assertNull(metaData.colorWhenBootstrappedFalse())
        metaData.ints[KEY_COLOR_WHEN_BOOTSTRAPPED_FALSE] = -1
        assertNull(metaData.colorWhenBootstrappedFalse())
    }

    @Test
    fun givenMetaData_whenColorBootstrappedFalse_thenChecksValid() {
        val expected = ColorRes.of(1)
        metaData.ints[KEY_COLOR_WHEN_BOOTSTRAPPED_FALSE] = expected.id
        assertEquals(expected, metaData.colorWhenBootstrappedFalse())
        assertEquals(1, metaData.invocationColorValid)
        metaData.colorIsValid = false
        assertFailsWith<IllegalStateException> { metaData.colorWhenBootstrappedFalse() }
    }

    @Test
    fun givenMetaData_whenVisibilityStringValid_thenReturnsType() {
        listOf(
            1 to "public",
            -1 to "secret",
            0 to null,
            0 to "null",
            0 to "private",
        ).forEach { (expected, string) ->
            if (string == null) {
                metaData.strings.remove(KEY_VISIBILITY)
            } else {
                metaData.strings[KEY_VISIBILITY] = string
            }

            assertEquals(expected, metaData.visibility())
        }
    }

    @Test
    fun givenMetaData_whenVisibilityStringInvalid_thenThrowsException() {
        metaData.strings[KEY_VISIBILITY] = "unknown"
        assertFailsWith<IllegalStateException> { metaData.visibility() }
        metaData.strings[KEY_VISIBILITY] = ""
        assertFailsWith<IllegalStateException> { metaData.visibility() }
    }

    @Test
    fun givenMetaData_whenEnableActionRestartNull_thenDefaultsToFalse() {
        assertFalse(metaData.enableActionRestart())
    }

    @Test
    fun givenMetaData_whenEnableActionRestartTrue_thenIsTrue() {
        metaData.booleans[KEY_ACTION_ENABLE_RESTART] = true
        assertTrue(metaData.enableActionRestart())
    }

    @Test
    fun givenMetaData_whenEnableActionRestartFalse_thenIsFalse() {
        metaData.booleans[KEY_ACTION_ENABLE_RESTART] = false
        assertFalse(metaData.enableActionRestart())
    }

    @Test
    fun givenMetaData_whenEnableActionStopNull_thenDefaultsToFalse() {
        assertFalse(metaData.enableActionStop())
    }

    @Test
    fun givenMetaData_whenEnableActionStopTrue_thenIsTrue() {
        metaData.booleans[KEY_ACTION_ENABLE_STOP] = true
        assertTrue(metaData.enableActionStop())
    }

    @Test
    fun givenMetaData_whenEnableActionStopFalse_thenIsFalse() {
        metaData.booleans[KEY_ACTION_ENABLE_STOP] = false
        assertFalse(metaData.enableActionStop())
    }

}
