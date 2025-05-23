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
package io.matthewnelson.kmp.tor.runtime.core.key

import io.matthewnelson.encoding.base16.Base16
import io.matthewnelson.encoding.base32.Base32Default
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import org.kotlincrypto.error.InvalidKeyException
import kotlin.test.*

abstract class KeyBaseUnitTest(protected val expectedAlgorithm: String) {

    protected abstract val publicKey: Key.Public
    protected abstract val privateKey: Key.Private

    protected fun assertInvalidKey(errorContains: List<String> = emptyList(), toKey: () -> Key) {
        try {
            toKey()
            fail("InvalidKeyException was not thrown as expected")
        } catch (e: InvalidKeyException) {
            // Also check messages for wrapped IllegalArgumentException (if present)
            val messages = listOf(e.message, e.cause?.message)

            errorContains.forEach c@ { expected ->
                messages.forEach m@ { message ->
                    if (message == null) return@m
                    if (message.contains(expected)) return@c
                }
                fail("""
                    contains: $expected
                    messages: $messages
                """.trimIndent())
            }
        }
    }

    @Test
    fun givenKey_whenAlgorithm_thenIsAsExpected() {
        assertEquals(expectedAlgorithm, publicKey.algorithm())
        assertEquals(expectedAlgorithm, privateKey.algorithm())
    }

    @Test
    fun givenKey_whenBase16_thenIsSingleLineUppercase() {
        val public = publicKey.base16()
        assertEquals(1, public.lines().size)
        public.forEach { assertEquals(it, it.uppercaseChar()) }

        val private = privateKey.base16()
        assertEquals(1, private.lines().size)
        private.forEach { assertEquals(it, it.uppercaseChar()) }
    }

    @Test
    fun givenKey_whenBase32_thenIsSingleLineUppercase() {
        val public = publicKey.base32()
        assertEquals(1, public.lines().size)
        public.forEach { assertEquals(it, it.uppercaseChar()) }

        val private = privateKey.base32()
        assertEquals(1, private.lines().size)
        private.forEach { assertEquals(it, it.uppercaseChar()) }
    }

    @Test
    fun givenKey_whenBase64_thenIsSingleLine() {
        assertEquals(1, publicKey.base64().lines().size)
        assertEquals(1, privateKey.base64().lines().size)
    }

    @Test
    fun givenPublicKey_whenEncoded_thenReturnedBytesAreACopy() {
        val bytes = publicKey.encoded()
        bytes.fill(5)

        try {
            assertContentEquals(publicKey.encoded(), bytes)
            throw IllegalStateException()
        } catch (_: AssertionError) {
            // pass
        }
    }

    @Test
    fun givenPrivateKey_whenEncoded_thenReturnedBytesAreACopy() {
        val bytes = privateKey.encoded()
        bytes.fill(5)

        try {
            assertContentEquals(privateKey.encoded(), bytes)
            throw IllegalStateException()
        } catch (_: AssertionError) {
            // pass
        }
    }

    @Test
    fun givenPublicKey_whenEncodings_thenAreAsExpected() {
        val bytes = publicKey.encoded()

        assertEquals(bytes.encodeToString(Base16()), publicKey.base16())
        assertEquals(bytes.encodeToString(Base32Default { padEncoded = false }), publicKey.base32())
        assertEquals(bytes.encodeToString(Base64 { padEncoded = false }), publicKey.base64())
    }

    @Test
    fun givenPrivateKey_whenEncodings_thenAreAsExpected() {
        val bytes = privateKey.encoded()

        assertEquals(bytes.encodeToString(Base16()), privateKey.base16())
        assertEquals(bytes.encodeToString(Base32Default { padEncoded = false }), privateKey.base32())
        assertEquals(bytes.encodeToString(Base64 { padEncoded = false }), privateKey.base64())
    }

    @Test
    fun givenPrivateKey_whenDestroyed_thenThingsReturnAsExpected() {
        assertFalse(privateKey.isDestroyed())
        privateKey.destroy()
        assertTrue(privateKey.isDestroyed())
        assertNull(privateKey.base16OrNull())
        assertNull(privateKey.base32OrNull())
        assertNull(privateKey.base64OrNull())
        assertNull(privateKey.encodedOrNull())

        fun assertDestroyed(block: Key.Private.() -> Unit) {
            try {
                block(privateKey)
                fail()
            } catch (e: IllegalStateException) {
                val message = e.message
                assertNotNull(message)
                assertTrue(message.startsWith(privateKey.algorithm()))
                assertTrue(message.endsWith("isDestroyed[true]"))
            }
        }

        assertDestroyed { encoded() }
        assertDestroyed { base16() }
        assertDestroyed { base32() }
        assertDestroyed { base64() }

        // Does not throw b/c that'd be awful
        privateKey.toString()
    }

    @Test
    fun givenPublicKey_whenToString_thenFormatIsAsExpected() {
        val expected = expectedAlgorithm + ".PublicKey[" + publicKey.base32() + "]"
        assertEquals(expected, publicKey.toString())
    }

    @Test
    fun givenPrivateKey_whenToString_thenFormatIsAsExpected() {
        val expected = expectedAlgorithm + ".PrivateKey[isDestroyed=false]@" + privateKey.hashCode()
        assertEquals(expected, privateKey.toString())
        privateKey.destroy()
        assertTrue(privateKey.toString().contains("[isDestroyed=true]"))
    }
}
