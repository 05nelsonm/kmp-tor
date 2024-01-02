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
package io.matthewnelson.kmp.tor.runtime.api.config.builders

import io.matthewnelson.kmp.file.SysPathSep
import io.matthewnelson.kmp.file.path
import io.matthewnelson.kmp.file.toFile
import kotlin.test.*

abstract class UnixSocketBuilderBaseTest {

    protected abstract val hostSupportsUnixSockets: Boolean

    @Test
    fun givenFilePath_whenValid_thenIsFormattedProperly() {
        if (!hostSupportsUnixSockets) {
            printSkipTest()
            return
        }

        val path = UnixSocketBuilder.build { file = "/some/path".toFile() }!!
        assertTrue(path.startsWith("unix:\""))
        assertEquals('"', path.last())
    }

    @Test
    fun givenFilePath_whenExceedsMaxLength_thenThrowsException() {
        if (!hostSupportsUnixSockets) {
            printSkipTest()
            return
        }

        val path = buildString {
            // rooted so current working dir (test dir) isn't resolved
            append(SysPathSep)
            // sep + 104 characters (maximum size allowed is 105)
            repeat(104) { append('a') }
        }

        assertNotNull(UnixSocketBuilder.build { file = path.toFile() })

        assertFailsWith<UnsupportedOperationException> {
            // 106 characters
            UnixSocketBuilder.build { file = (path + "a").toFile() }
        }
    }

    @Test
    fun givenFilePath_whenMultiLine_thenThrowsException() {
        if (!hostSupportsUnixSockets) {
            printSkipTest()
            return
        }

        val path = buildString {
            append(SysPathSep)
            repeat(15) { append('a') }
            appendLine()
            repeat(15) { append('a') }
        }.toFile()

        assertEquals(2, path.path.lines().size)

        assertFailsWith<UnsupportedOperationException> {
            UnixSocketBuilder.build { file = path }
        }
    }

    @Test
    fun givenNoUnixSocketSupport_whenBuildCalled_thenThrowsException() {
        if (hostSupportsUnixSockets) {
            printSkipTest()
            return
        }

        assertFailsWith<UnsupportedOperationException> {
            UnixSocketBuilder.build {}
        }
    }

    protected fun printSkipTest() { println("Skipping test...") }
}
