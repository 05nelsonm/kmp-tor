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
package io.matthewnelson.kmp.tor.runtime.core.internal

import io.matthewnelson.kmp.tor.common.api.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.core.OnEvent
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MainExecutorJvmUnitTest {

    @Test
    fun givenExecute_whenNoDispatchersMain_thenThrowsException() {
        assertFailsWith<IllegalStateException> {
            @OptIn(InternalKmpTorApi::class)
            OnEvent.Executor.Main.execute(EmptyCoroutineContext) {  }
        }
    }

    @Test
    fun givenComposeDesktop_whenConfigureSwingGlobalsPropertyKeyPresent_thenRetrievesMainUIDispatcher() {
        val key = "compose.application.configure.swing.globals"

        assertNull(System.getProperty(key))

        System.setProperty(key, "true")

        try {
            assertNotNull(Dispatchers.composeDesktopUIDispatcherOrNull())
        } finally {
            System.clearProperty(key)
        }
    }
}
