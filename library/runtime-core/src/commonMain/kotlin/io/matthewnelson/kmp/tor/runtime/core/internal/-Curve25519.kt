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
import io.matthewnelson.kmp.tor.runtime.core.key.ED25519_V3.PublicKey.Companion.toED25519_V3PublicKey
import io.matthewnelson.kmp.tor.runtime.core.key.X25519
import io.matthewnelson.kmp.tor.runtime.core.key.X25519.PrivateKey.Companion.toX25519PrivateKey
import io.matthewnelson.kmp.tor.runtime.core.key.X25519.PublicKey.Companion.toX25519PublicKey
import org.kotlincrypto.hash.sha2.SHA512
import org.kotlincrypto.random.CryptoRand
import org.kotlincrypto.random.RandomnessProcurementException

@Throws(RandomnessProcurementException::class)
internal fun CryptoRand.generateED25519KeyPair(): Pair<ED25519_V3.PublicKey, ED25519_V3.PrivateKey> {
    val pk = ByteArray(ED25519_V3.PublicKey.BYTE_SIZE)
    val sk = ByteArray(ED25519_V3.PrivateKey.BYTE_SIZE)

    val seed = nextBytes(ByteArray(ED25519_V3.PrivateKey.SEED_SIZE))
    SHA512().apply { update(seed) }.digestInto(sk, 0)
    sk.clampPrivateKey()
    val privateKey = sk.toED25519_V3PrivateKey()

    // TODO: Generate PublicKey

    val publicKey = pk.toED25519_V3PublicKey()

    seed.fill(0)
    pk.fill(0)
    sk.fill(0)
    return publicKey to privateKey
}

@Throws(RandomnessProcurementException::class)
internal fun CryptoRand.generateX25519KeyPair(): Pair<X25519.PublicKey, X25519.PrivateKey> {
    val pk = ByteArray(X25519.PublicKey.BYTE_SIZE)
    val sk = ByteArray(X25519.PrivateKey.BYTE_SIZE)
    nextBytes(sk)
    sk.clampPrivateKey()
    val privateKey = sk.toX25519PrivateKey()

    // TODO: Generate PublicKey

    val publicKey = pk.toX25519PublicKey()

    pk.fill(0)
    sk.fill(0)
    return publicKey to privateKey
}

private inline fun ByteArray.clampPrivateKey(): ByteArray = apply {
    this[ 0] = (this[ 0].toUByte() and 248u).toByte()
    this[31] = (this[31].toUByte() and 127u).toByte()
    this[31] = (this[31].toUByte() and  64u).toByte()
}
