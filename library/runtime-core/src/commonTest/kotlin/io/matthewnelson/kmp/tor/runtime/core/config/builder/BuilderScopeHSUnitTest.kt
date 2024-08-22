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
package io.matthewnelson.kmp.tor.runtime.core.config.builder

import io.matthewnelson.kmp.file.absoluteFile
import io.matthewnelson.kmp.file.path
import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.tor.runtime.core.address.Port
import io.matthewnelson.kmp.tor.runtime.core.address.Port.Companion.toPort
import io.matthewnelson.kmp.tor.runtime.core.config.TorOption.*
import kotlin.test.*

class BuilderScopeHSUnitTest {

    @Test
    fun givenBuilder_whenMinimumRequirementsNotMet_thenThrowsException() {
        val setting = HiddenServiceDir.asSetting {
            // Must contain directory
            try {
                build()
                fail()
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.contains(HiddenServiceDir.name))
            }
            directory("".toFile())

            // Must contain version
            try {
                build()
                fail()
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.contains(HiddenServiceVersion.name))
            }
            version(HiddenServiceVersion.default.toInt())

            // Must contain at least 1 port
            try {
                build()
                fail()
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.contains(HiddenServicePort.name))
            }
            port(virtual = Port.HTTP)
        }

        assertEquals(3, setting.items.size)
    }

    @Test
    fun givenBuilder_whenInvalidVersion_thenThrowsException() {
        val setting = HiddenServiceDir.asSetting {
            applyRequiredForTest()
            build()

            version(1)
            assertFailsWith<IllegalArgumentException> { build() }

            version(3)
        }

        assertEquals(3, setting.items.size)
        assertEquals("3", setting.items.elementAt(1).argument)
    }

    @Test
    fun givenBuilder_whenDirectory_thenIsAbsoluteNormalized() {
        val expected = "1234else"
        val absolute = "".toFile().absoluteFile
        val dir = absolute.resolve("somewhere/$expected/another/../.")

        val setting = HiddenServiceDir.asSetting {
            applyRequiredForTest()
            directory(dir)
        }

        assertEquals(3, setting.items.size)
        val argument = setting.items.first().argument
        assertTrue(absolute.path.isNotEmpty())
        assertTrue(argument.startsWith(absolute.path), "argument didn't start with absolute[$absolute] >>> $argument")
        assertTrue(argument.endsWith(expected), "argument didn't end with expected[$expected] >>> $argument")
    }

    @Test
    fun givenBuilder_whenMultiplePorts_thenResultHasExpected() {
        val setting = HiddenServiceDir.asSetting {
            applyRequiredForTest()
            port(virtual = Port.HTTP) {
                target(port = 8080.toPort())
            }

            // Same, should not contain duplicate
            port(virtual = Port.HTTP) {
                target(port = 8080.toPort())
            }
        }

        assertEquals(4, setting.items.size)
        assertEquals(2, setting.items.filter { it.option is HiddenServicePort }.size)
    }

    @Test
    fun givenBuilder_whenAllConfigured_thenResultContainsAllSettings() {
        val items = HiddenServiceDir.asSetting {
            applyRequiredForTest()
            port(virtual = Port.HTTPS) { target(port = 8443.toPort()) }
            onionBalanceInstance(true)
            dirGroupReadable(true)
            maxStreams(25)
            maxStreamsCloseCircuit(true)
            allowUnknownPorts(true)
            numIntroductionPoints(HiddenServiceNumIntroductionPoints.default.toInt())
        }.items

        assertIs<HiddenServiceDir>(items.elementAt(0).option)
        assertIs<HiddenServiceVersion>(items.elementAt(1).option)
        assertIs<HiddenServicePort>(items.elementAt(2).option)
        assertIs<HiddenServicePort>(items.elementAt(3).option)
        assertIs<HiddenServiceAllowUnknownPorts>(items.elementAt(4).option)
        assertIs<HiddenServiceDirGroupReadable>(items.elementAt(5).option)
        assertIs<HiddenServiceOnionBalanceInstance>(items.elementAt(6).option)
        assertIs<HiddenServiceMaxStreams>(items.elementAt(7).option)
        assertIs<HiddenServiceMaxStreamsCloseCircuit>(items.elementAt(8).option)
        assertIs<HiddenServiceNumIntroductionPoints>(items.elementAt(9).option)
    }

    private fun BuilderScopeHS.applyRequiredForTest() {
        directory("".toFile())
        version(HiddenServiceVersion.default.toInt())
        port(virtual = Port.HTTP)
    }
}
