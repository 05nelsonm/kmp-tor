/*
 * Copyright (c) 2025 Matthew Nelson
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

import org.bouncycastle.math.ec.rfc7748.X25519
import org.bouncycastle.math.ec.rfc8032.Ed25519
import org.kotlincrypto.hash.sha2.SHA512
import org.kotlincrypto.random.CryptoRand
import org.kotlincrypto.random.DelicateCryptoRandApi
import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(DelicateCryptoRandApi::class)
class Curve25519JvmUnitTest {

    private val seeds = Array(50) { CryptoRand.Default.nextBytes(ByteArray(32)) }
    private val crands = seeds.map { seed ->
        object : CryptoRand() {
            override fun nextBytes(buf: ByteArray): ByteArray {
                assertEquals(seed.size, buf.size)
                seed.copyInto(buf)
                return buf
            }
        }
    }
    private val srands = seeds.map { seed ->
        object : SecureRandom() {
            override fun nextBytes(bytes: ByteArray?) {
                assertNotNull(bytes)
                assertEquals(seed.size, bytes.size)
                seed.copyInto(bytes)
            }
        }
    }

    @Test
    fun givenX25519_whenGenerateKeyPair_thenIsExpected() {
        val kps = crands.map { r ->
            val s = r.generateX25519PrivateKey()
            val p = s.generatePublicKey()
            p.encoded() to s.encoded()
        }

        val kpsBC = srands.map { r ->
            val sk = ByteArray(32)
            val pk = sk.copyOf()
            X25519.generatePrivateKey(r, sk)
            X25519.generatePublicKey(sk, 0, pk, 0)
            pk to sk
        }

        for (i in kps.indices) {
            val (p, s) = kps[i]
            val (pBC, sBC) = kpsBC[i]

            assertContentEquals(pBC, p)
            assertContentEquals(sBC, s)
        }
    }

    @Test
    fun givenED25519_whenGenerateKeyPair_thenIsExpected() {
        val kps = crands.map { r ->
            val s = r.generateED25519PrivateKey()
            val p = s.generatePublicKey()
            p.encoded() to s.encoded()
        }

        val kpsBC = srands.map { r ->
            val sk = ByteArray(32)
            val pk = sk.copyOf()
            Ed25519.generatePrivateKey(r, sk)
            Ed25519.generatePublicKey(sk, 0, pk, 0)
            pk to sk
        }

        for (i in kps.indices) {
            val (p, s) = kps[i]
            val (pBC, sBC) = kpsBC[i]

            // kmp-tor ED25519_V3.PrivateKey is stored in its extended format
            val sBCExt = SHA512().digest(sBC)
            sBCExt[ 0] = (sBCExt[ 0].toUByte() and 248u).toByte()
            sBCExt[31] = (sBCExt[31].toUByte() and 127u).toByte()
            sBCExt[31] = (sBCExt[31].toUByte()  or  64u).toByte()

            assertContentEquals(pBC, p)
            assertContentEquals(sBCExt, s)
        }
    }
}
