/*
 * Copyright (c) 2023 Matthew Nelson
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
package io.matthewnelson.kmp.tor.runtime.api.config

import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.tor.runtime.api.config.TorConfig.Setting.Companion.filterByAttribute
import io.matthewnelson.kmp.tor.runtime.api.config.TorConfig.Setting.Companion.filterByKeyword
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
