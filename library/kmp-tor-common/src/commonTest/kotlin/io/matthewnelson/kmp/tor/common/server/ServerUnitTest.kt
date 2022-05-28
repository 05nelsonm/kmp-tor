/*
 * Copyright (c) 2022 Matthew Nelson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
package io.matthewnelson.kmp.tor.common.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ServerUnitTest {

    companion object {
        const val FINGERPRINT = "F00EC2E0A2CA79A57FE7A0918A087987747D772D"
    }

    @Test
    fun givenServerFingerprint_whenPrefixPresent_prefixIsStripped() {
        val fp = Server.Fingerprint(Server.Fingerprint.PREFIX + FINGERPRINT)

        assertEquals(FINGERPRINT.first(), fp.value.first())
    }

    @Test
    fun givenServerFingerprint_whenDecoded_returnsBytes() {
        // Should not throw Kotlin Null Pointer
        Server.Fingerprint(FINGERPRINT).decode()
    }

    @Test
    fun givenServerLongNameFromString_whenNoNickname_returnsWithNullNickname() {
        val longName = Server.LongName.fromString(FINGERPRINT)
        assertNull(longName.nickname)
    }

    @Test
    fun givenServerLongNameFromString_whenEqualsDelimiter_returnsSuccessfully() {
        val expectedNickname = "ValidNickname"
        val longName = Server.LongName.fromString("$FINGERPRINT=$expectedNickname")

        assertEquals(FINGERPRINT, longName.fingerprint.value)
        assertEquals(expectedNickname, longName.nickname?.value)
    }

    @Test
    fun givenServerLongNameFromString_whenTildeDelimiter_returnsSuccessfully() {
        val expectedNickname = "ValidNickname"
        val longName = Server.LongName.fromString("$FINGERPRINT~$expectedNickname")

        assertEquals(FINGERPRINT, longName.fingerprint.value)
        assertEquals(expectedNickname, longName.nickname?.value)
    }
}
