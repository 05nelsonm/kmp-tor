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
package io.matthewnelson.kmp.tor.runtime.internal.process

import kotlin.test.*

class StartupFeedParserUnitTest {

    @Test
    fun givenParser_whenThrowError_thenCauseIsNotAppendedToStdout() {
        val parser = StartupFeedParser { null }

        listOf("expected1", "expected2").forEach { expected ->
            val ex = parser.createError(expected)

            assertTrue(ex.message.contains("exitCode: not exited"))
            assertTrue(ex.message.contains("cause: $expected"))

            // if this is expected2, expected1 cause should have been
            // appended to the empty StringBuilder, not stdout.
            assertTrue(ex.message.contains("stdout: []"))
            assertTrue(ex.message.contains("stderr: []"))

            // _error is set as the current error message
            try {
                parser.checkError()
                fail()
            } catch (e: ProcessStartException) {
                assertEquals(ex, e)
            }
        }
    }

    @Test
    fun givenLineLimit_whenExceededAndNotReady_thenGeneratesError() {
        val parser = StartupFeedParser(lineLimit = 3) { null }
        parser.stdout.onOutput("1")
        parser.stdout.onOutput("2")
        parser.stdout.onOutput("3")
        parser.checkError()
        parser.stdout.onOutput("4")
        assertFailsWith<ProcessStartException> { parser.checkError() }
    }

    @Test
    fun givenLineLimit_whenExceededAndReady_thenDoesNotGeneratesError() {
        val out = """
            May 28 11:17:14.295 [notice] Tor 0.4.8.10 running on Linux with Libevent 2.1.12-stable, OpenSSL 3.2.0, Zlib 1.3, Liblzma 5.4.5, Libzstd N/A and Glibc 2.35 as libc.
            May 28 11:17:14.295 [notice] Tor can't help you if you use it wrong! Learn how to be safe at https://support.torproject.org/faq/staying-anonymous/
            May 28 11:17:14.295 [notice] Read configuration file "/tmp/kmp_tor_test/obs_conn_no_net/work/torrc-defaults".
            May 28 11:17:14.295 [notice] Read configuration file "/tmp/kmp_tor_test/obs_conn_no_net/work/torrc".
            May 28 11:17:14.296 [notice] Opening Control listener on /tmp/kmp_tor_test/obs_conn_no_net/work/ctrl.sock
            May 28 11:17:14.296 [notice] Opened Control listener connection (ready) on /tmp/kmp_tor_test/obs_conn_no_net/work/ctrl.sock
        """.trimIndent()

        val lines = out.lines()

        val parser = StartupFeedParser(lineLimit = lines.size) { null }
        assertFalse(parser.isReady)
        lines.forEach { line -> parser.stdout.onOutput(line) }
        assertTrue(parser.isReady)
        parser.checkError()

        parser.stdout.onOutput("something else")
        parser.checkError()
    }

    @Test
    fun givenTorError_whenCreated_thenContainsInErrorMessage() {
        val out = """
            May 28 11:21:32.559 [notice] Tor 0.4.8.10 running on Linux with Libevent 2.1.12-stable, OpenSSL 3.2.0, Zlib 1.3, Liblzma 5.4.5, Libzstd N/A and Glibc 2.35 as libc.
            May 28 11:21:32.559 [notice] Tor can't help you if you use it wrong! Learn how to be safe at https://support.torproject.org/faq/staying-anonymous/
            May 28 11:21:32.559 [notice] Read configuration file "/tmp/kmp_tor_test/rt_start_fail/work/torrc-defaults".
            May 28 11:21:32.559 [notice] Read configuration file "/tmp/kmp_tor_test/rt_start_fail/work/torrc".
            May 28 11:21:32.560 [warn] Couldn't parse address "-1" for DNSPort
            May 28 11:21:32.560 [warn] Failed to parse/validate config: Invalid DNSPort configuration
            May 28 11:21:32.560 [err] Reading config failed--see warnings above.
        """.trimIndent()

        val lines = out.lines()

        val parser = StartupFeedParser { null }
        lines.forEach { line -> parser.stdout.onOutput(line) }

        try {
            parser.checkError()
            fail()
        } catch (e: ProcessStartException) {
            lines.forEach { line -> assertTrue(e.message.contains(line)) }
            // last line was used for the error cause
            e.message.contains("    cause: ${lines.last()}")
        }
    }

    @Test
    fun givenNullOutput_whenLineLimitNotExceeded_thenCreatesException() {
        val parser = StartupFeedParser(lineLimit = 2) { null }
        parser.stdout.onOutput("1")
        parser.stdout.onOutput(null)
        assertFailsWith<ProcessStartException> { parser.checkError() }
    }

    @Test
    fun givenOutput_whenExceptionCreated_thenIsPresentInMessage() {
        val parser = StartupFeedParser(lineLimit = 3) { null }
        parser.stderr.onOutput("STDERR")
        parser.stdout.onOutput("STDOUT")
        parser.stdout.onOutput("STDOUT")
        parser.stdout.onOutput("STDOUT")
        parser.stdout.onOutput("STDOUT")
        try {
            parser.checkError()
            fail()
        } catch (e: ProcessStartException) {
            e.message.contains("STDOUT")
            e.message.contains("STDERR")
        }
    }

    @Test
    fun givenParser_whenClose_thenClearsOutputAndDereferences() {
        val parser = StartupFeedParser { 2 }
        parser.stdout.onOutput("STDOUT")
        parser.stderr.onOutput("STDERR")
        parser.createError("something").message.let { message ->
            assertTrue(message.contains("STDOUT"))
            assertTrue(message.contains("STDERR"))
        }
        parser.close()
        val ex = parser.createError("something")
        ex.message.let { message ->
            assertFalse(message.contains("STDOUT"))
            assertFalse(message.contains("STDERR"))
        }

        // confirming it was set as our exception
        try {
            parser.checkError()
            fail()
        } catch (e: ProcessStartException) {
            assertEquals(ex, e)
        }

        // Should create a "Process exited unexpectedly" exception
        parser.stdout.onOutput(null)
        try {
            parser.checkError()
            fail()
        } catch (e: ProcessStartException) {
            // onOutput did nothing, it was closed
            assertEquals(ex, e)
        }
    }
}
