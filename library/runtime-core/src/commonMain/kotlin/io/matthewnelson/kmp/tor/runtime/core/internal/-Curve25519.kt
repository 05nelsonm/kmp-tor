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
@file:Suppress("KotlinRedundantDiagnosticSuppress", "NOTHING_TO_INLINE")

package io.matthewnelson.kmp.tor.runtime.core.internal

import io.matthewnelson.kmp.tor.runtime.core.key.ED25519_V3
import io.matthewnelson.kmp.tor.runtime.core.key.ED25519_V3.PrivateKey.Companion.toED25519_V3PrivateKey
import io.matthewnelson.kmp.tor.runtime.core.key.X25519
import io.matthewnelson.kmp.tor.runtime.core.key.X25519.PrivateKey.Companion.toX25519PrivateKey
import org.kotlincrypto.hash.sha2.SHA512
import org.kotlincrypto.random.CryptoRand
import org.kotlincrypto.random.RandomnessProcurementException
import kotlin.jvm.JvmSynthetic

@JvmSynthetic
@Throws(RandomnessProcurementException::class)
internal fun CryptoRand.generateED25519KeyPair(): Pair<ED25519_V3.PublicKey, ED25519_V3.PrivateKey> {
    val seed = nextBytes(ByteArray(32))
    // Tor stores ed25519 keys in their 64-byte expanded format
    val b = SHA512().digest(seed).clampPrivateKey()
    val private = b.toED25519_V3PrivateKey()

    seed.fill(0)
    b.fill(0)
    throw RandomnessProcurementException("Not yet implemented")
}

@JvmSynthetic
@Throws(RandomnessProcurementException::class)
internal fun CryptoRand.generateX25519KeyPair(): Pair<X25519.PublicKey, X25519.PrivateKey> {
    val b = nextBytes(ByteArray(32)).clampPrivateKey()
    val private = b.toX25519PrivateKey()

    b.fill(0)
    throw RandomnessProcurementException("Not yet implemented")
}

private inline fun ByteArray.clampPrivateKey(): ByteArray = apply {
    this[ 0] = (this[ 0].toUByte() and 248.toUByte()).toByte()
    this[31] = (this[31].toUByte() and 127.toUByte()).toByte()
    this[31] = (this[31].toUByte()  or  64.toUByte()).toByte()
}
