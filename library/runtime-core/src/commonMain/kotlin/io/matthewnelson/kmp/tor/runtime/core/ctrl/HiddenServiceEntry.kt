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
package io.matthewnelson.kmp.tor.runtime.core.ctrl

import io.matthewnelson.immutable.collections.toImmutableSet
import io.matthewnelson.kmp.tor.runtime.core.key.AddressKey
import io.matthewnelson.kmp.tor.runtime.core.key.AuthKey
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * Holder for a hidden service public/private key pair returned
 * by a control connection using command [TorCmd.Onion.Add].
 *
 * @see [of]
 * @see [TorCmd.Onion.Add]
 * */
public class HiddenServiceEntry private constructor(
    @JvmField
    public val publicKey: AddressKey.Public,
    @JvmField
    public val privateKey: AddressKey.Private?,
    @JvmField
    public val clientAuth: Set<AuthKey.Public>,
) {

    public companion object {

        /**
         * Creates a new [HiddenServiceEntry] for provided key(s).
         *
         * @throws [IllegalArgumentException] if key algorithms do not match.
         * */
        @JvmStatic
        @Throws(IllegalArgumentException::class)
        public fun of(
            publicKey: AddressKey.Public,
            privateKey: AddressKey.Private?,
            clientAuth: Set<AuthKey.Public>,
        ): HiddenServiceEntry {
            if (privateKey == null) {
                return HiddenServiceEntry(publicKey, privateKey, clientAuth)
            }

            val aPublic = publicKey.algorithm()
            val aPrivate = privateKey.algorithm()

            require(aPublic == aPrivate) {
                "Incompatible key types. " +
                "AddressKey.PublicKey[$aPublic]. " +
                "AddressKey.PrivateKey[$aPrivate]"
            }

            return HiddenServiceEntry(
                publicKey = publicKey,
                privateKey = privateKey,
                clientAuth = clientAuth.toImmutableSet(),
            )
        }
    }

    public override fun equals(other: Any?): Boolean {
        return  other is HiddenServiceEntry
                && other.publicKey == publicKey
                && other.privateKey == privateKey
                && other.clientAuth == clientAuth
    }

    public override fun hashCode(): Int {
        var result = 21
        result = result * 42 + publicKey.hashCode()
        result = result * 42 + privateKey.hashCode()
        result = result * 42 + clientAuth.hashCode()
        return result
    }

    public override fun toString(): String = buildString {
        appendLine("HiddenServiceEntry: [")
        append("    publicKey: ")
        appendLine(publicKey)
        append("    privateKey: ")
        appendLine(privateKey.toString())

        append("    clientAuth: [")
        if (clientAuth.isEmpty()) {
            append(']')
        } else {
            for (key in clientAuth) {
                appendLine()
                append("        ")
                append(key)
            }
            appendLine()
            append("    ]")
        }

        appendLine()
        append(']')
    }
}
