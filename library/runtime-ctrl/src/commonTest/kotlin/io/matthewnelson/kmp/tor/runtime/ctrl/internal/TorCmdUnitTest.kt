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
package io.matthewnelson.kmp.tor.runtime.ctrl.internal

import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TorCmdUnitTest {

    @Test
    fun givenConfigLoad_whenEncode_thenThrowsWhenNoSettings() {
        var invocationLOG = 0
        val LOG = Debugger.of("") { invocationLOG++ }

        val blank = "    "

        listOf(
            "",
            """
                # comment
            """.trimIndent(),
            """
                # comment
                
                $blank
                  # indented comment
            """.trimIndent()
        ).forEach { text ->
            assertFailsWith<IllegalArgumentException> {
                TorCmd.Config.Load(text).encodeToByteArray(LOG)
            }
        }

        assertEquals(0, invocationLOG)
    }

    @Test
    fun givenConfigLoad_whenEncode_thenRemovesComments() {
        val expected = "SocksPort 9050"
        val lines = TorCmd.Config.Load(configText = """
            # comment
              # indented comment

            $expected
        """.trimIndent())
            .encodeToByteArray(null)
            .decodeToString()
            .lines()

        // +LOADCONF
        // SocksPort 9050
        // .
        //
        assertEquals(4, lines.size)
        assertTrue(lines.contains(expected))
    }
}
