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
package io.matthewnelson.kmp.tor.runtime.core.key

import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import io.matthewnelson.kmp.tor.runtime.core.net.OnionAddress
import kotlin.jvm.JvmSynthetic

/**
 * Type definition of [Key.Public] and [Key.Private] specific to
 * [OnionAddress].
 * */
public class AddressKey private constructor() {

    /**
     * Wrapper to extend [OnionAddress] capabilities for public key usage.
     *
     * @see [OnionAddress.asPublicKey]
     * @see [ED25519_V3.PublicKey]
     * */
    public sealed class Public(private val onionAddress: OnionAddress): Key.Public(), Comparable<Public> {

        public open fun address(): OnionAddress = onionAddress

        public final override fun encoded(): ByteArray = when (this) {
            is ED25519_V3.PublicKey -> ED25519_V3.PublicKey.BYTE_SIZE
        }.let { size -> onionAddress.decode().copyOf(size) }

        public final override fun base16(): String = encoded().encodeToString(BASE_16)
        public final override fun base32(): String = encoded().encodeToString(BASE_32)
        public final override fun base64(): String = encoded().encodeToString(BASE_64)

        public final override fun compareTo(other: AddressKey.Public): Int = onionAddress.compareTo(other.onionAddress)
    }

    /**
     * Holder for an [OnionAddress] private key. Private keys are utilized
     * when creating Hidden Services.
     *
     * @see [ED25519_V3.PrivateKey]
     * */
    public sealed class Private(key: ByteArray): Key.Private(key) {

        @JvmSynthetic
        internal fun type(): KeyType.Address<*, *> = when (this) {
            is ED25519_V3.PrivateKey -> ED25519_V3
        }
    }

    init {
        throw IllegalStateException("AddressKey cannot be instantiated")
    }
}
