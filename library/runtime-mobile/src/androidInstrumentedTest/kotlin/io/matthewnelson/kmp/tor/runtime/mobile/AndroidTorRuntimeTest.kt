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
package io.matthewnelson.kmp.tor.runtime.mobile

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.matthewnelson.kmp.tor.core.api.ResourceInstaller
import io.matthewnelson.kmp.tor.core.api.ResourceInstaller.Paths
import io.matthewnelson.kmp.tor.runtime.Lifecycle
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent
import io.matthewnelson.kmp.tor.runtime.TorRuntime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class AndroidTorRuntimeTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun givenTorRuntime_whenAndroidRuntime_thenIsAndroidTorRuntime() {
        val environment = app.createTorRuntimeEnvironment { installationDir ->
            object : ResourceInstaller<Paths.Tor>(installationDir) {
                override fun install(): Paths.Tor { fail() }
            }
        }

        val lces = mutableListOf<Lifecycle.Event>()
        val runtime = TorRuntime.Builder(environment) {
            observerStatic(RuntimeEvent.LIFECYCLE) { lces.add(it) }
        }

        assertEquals("AndroidTorRuntime", runtime::class.simpleName)
        val lce = lces.filter { it.clazz == "AndroidTorRuntime" }
        assertEquals(1, lce.size)
        assertEquals(Lifecycle.Event.Name.OnCreate, lce.first().name)
    }
}
