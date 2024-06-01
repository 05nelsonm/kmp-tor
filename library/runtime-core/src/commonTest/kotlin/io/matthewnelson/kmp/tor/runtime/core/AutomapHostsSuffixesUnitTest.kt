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
package io.matthewnelson.kmp.tor.runtime.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class AutomapHostsSuffixesUnitTest {

    @Test
    fun givenAllPresent_whenAddingAnotherSuffix_thenDoesNotAdd() {
        val setting = TorConfig.AutomapHostsSuffixes.Builder {
            all()
            add(".exit")
        }

        assertEquals(".", setting.argument)
    }

    @Test
    fun givenSuffixesPresent_whenAddAll_thenClearsSuffixes() {
        val setting = TorConfig.AutomapHostsSuffixes.Builder {
            add(".exit")
            all()
        }

        assertEquals(".", setting.argument)
    }

    @Test
    fun givenSuffix_whenMissingDot_thenAddsIt() {
        val setting = TorConfig.AutomapHostsSuffixes.Builder { add("exit") }
        assertEquals(".exit", setting.argument)
    }

    @Test
    fun givenMultipleSuffix_whenBuild_thenJoinsAsCSV() {
        val setting = TorConfig.AutomapHostsSuffixes.Builder {
            add("exit")
            add("com")
        }
        assertEquals(".exit,.com", setting.argument)
    }

    @Test
    fun givenEmptySuffixes_whenBuild_thenUsesDefault() {
        val setting = TorConfig.AutomapHostsSuffixes.Builder {}
        assertEquals(".onion,.exit", setting.argument)
    }

    @Test
    fun givenInvalidSuffixes_whenAdd_thenThrowsException() {
        TorConfig.AutomapHostsSuffixes.Builder {
            assertFails { add("/illegal") }
            assertFails { add("\\illegal") }
            assertFails { add(".multi.dot") }
            assertFails { add("com,ma") }
            assertFails { add("spa ce") }
            assertFails { add("""multi-line

            """.trimIndent()) }
            assertFails { add(buildString {
                repeat(64) { append('a') }
            }) }
        }
    }
}
