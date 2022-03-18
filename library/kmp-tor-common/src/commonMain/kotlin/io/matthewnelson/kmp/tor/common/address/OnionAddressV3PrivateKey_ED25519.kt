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
package io.matthewnelson.kmp.tor.common.address

import io.matthewnelson.component.base64.Base64
import io.matthewnelson.component.base64.decodeBase64ToArray
import io.matthewnelson.kmp.tor.common.annotation.InternalTorApi
import io.matthewnelson.kmp.tor.common.clientauth.OnionClientAuthPrivateKey_B64_X25519.Companion.REGEX
import io.matthewnelson.kmp.tor.common.util.TorStrings.REDACTED
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmStatic

/**
 * Holder for a valid base64 encoded (without padding '=') ED25519-V3 onion address private key
 *
 * @see [REGEX] for private key character requirements
 * @throws [IllegalArgumentException] if [value] is not an 86 character base64
 *  encoded (without padding '=') String
 * */
@JvmInline
@OptIn(InternalTorApi::class)
@Suppress("ClassName")
value class OnionAddressV3PrivateKey_ED25519(override val value: String): OnionAddress.PrivateKey {

    init {
        require(value.matches(REGEX)) {
            "value=$REDACTED was not an 86 character base64 encoded (w/o padding '=' String"
        }
    }

    override fun decode(): ByteArray {
        return value.decodeBase64ToArray()!!
    }

    override val keyType: OnionAddress.PrivateKey.Type get() = OnionAddress.PrivateKey.Type.ED25519_V3

    override fun toString(): String {
        return "OnionAddressV3PrivateKey_ED25519(value=$REDACTED)"
    }

    companion object {
        @get:JvmStatic
        val REGEX: Regex get() = "[${Base64.Default.CHARS}]{86}".toRegex()
    }
}
