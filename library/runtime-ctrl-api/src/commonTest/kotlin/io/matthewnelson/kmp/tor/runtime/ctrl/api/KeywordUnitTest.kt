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
package io.matthewnelson.kmp.tor.runtime.ctrl.api

import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.Port.Companion.toPort
import io.matthewnelson.kmp.tor.runtime.ctrl.api.TorConfig.Setting.Companion.filterByAttribute
import io.matthewnelson.kmp.tor.runtime.ctrl.api.TorConfig.Setting.Companion.filterByKeyword
import io.matthewnelson.kmp.tor.runtime.ctrl.api.internal.UnixSocketsNotSupportedMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class KeywordUnitTest {

    @Test
    fun givenSettings_whenFilter_thenReturnsExpected() {
        val list = mutableListOf<TorConfig.Setting>()
        list.add(TorConfig.RunAsDaemon.Builder { enable = true })
        list.add(TorConfig.__OwningControllerProcess.Builder { processId = 22 }!!)
        list.add(TorConfig.DataDirectory.Builder { directory = File(".") }!!)

        assertEquals(3, list.size)
        assertEquals(1, list.filterByAttribute<TorConfig.Keyword.Attribute.Directory>().size)
        assertEquals(1, list.filterByKeyword<TorConfig.RunAsDaemon.Companion>().size)
    }

    @Test
    fun givenUnixSocketOrPortSetting_whenFilterByAttribute_thenReturnsExpected() {
        if (UnixSocketsNotSupportedMessage != null) return

        val list = mutableListOf<TorConfig.Setting>()

        TorConfig.__ControlPort.Builder {
            asUnixSocket { file = "/some/path/ctrl.sock".toFile() }
        }.let { list.add(it) }

        TorConfig.HiddenServiceDir.Builder {
            val dir = "/path/to/data_dir/${TorConfig.HiddenServiceDir.DEFAULT_PARENT_DIR_NAME}/hs_test".toFile()
            directory = dir

            port {
                virtual = 80.toPort()
                targetAsUnixSocket {
                    file = dir.resolve("hs.sock")
                }
            }
            port {
                virtual = 80.toPort()
                targetAsPort {
                    target = 8080.toPort()
                }
            }
            version {
                HSv(3)
            }
        }.let { list.add(it!!) }

        TorConfig.__DNSPort.Builder {
            auto()
        }.let { list.add(it) }

        val byPort = list.filterByAttribute<TorConfig.Keyword.Attribute.Port>()
        assertEquals(2, byPort.size)
        assertNotNull(byPort.find { it.keyword is TorConfig.__DNSPort.Companion })
        assertNotNull(byPort.find { it.keyword is TorConfig.HiddenServiceDir.Companion })

        val byUnixSocket = list.filterByAttribute<TorConfig.Keyword.Attribute.UnixSocket>()
        assertEquals(2, byUnixSocket.size)
        assertNotNull(byUnixSocket.find { it.keyword is TorConfig.__ControlPort.Companion })
        assertNotNull(byUnixSocket.find { it.keyword is TorConfig.HiddenServiceDir.Companion })
    }
}
