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
import io.matthewnelson.kmp.tor.runtime.core.address.OnionAddress
import io.matthewnelson.kmp.tor.runtime.core.key.AuthKey
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * Holder for results from [TorCmd.OnionClientAuth.View]
 * */
public class ClientAuthEntry private constructor(
    @JvmField
    public val address: OnionAddress,
    @JvmField
    public val privateKey: AuthKey.Private,
    @JvmField
    public val clientName: String?,
    flags: Set<String>,
) {

    @JvmField
    public val flags: Set<String> = flags.toImmutableSet()

    public companion object {

        /**
         * Creates a new [ClientAuthEntry] for provided key(s).
         *
         * @throws [IllegalArgumentException] if key types are incompatible.
         * */
        @JvmStatic
        @Throws(IllegalArgumentException::class)
        public fun of(
            address: OnionAddress,
            privateKey: AuthKey.Private,
            clientName: String?,
            flags: Set<String>,
        ): ClientAuthEntry {
            val addressKey = address.asPublicKey()

            require(privateKey.isCompatibleWith(addressKey)) {
                "Incompatible key types." +
                " AddressKey.Public[${addressKey.algorithm()}]." +
                " AuthKey.Private[${privateKey.algorithm()}]"
            }

            return ClientAuthEntry(
                address = address,
                privateKey = privateKey,
                clientName = clientName,
                flags = flags,
            )
        }
    }

    public override fun equals(other: Any?): Boolean {
        return  other is ClientAuthEntry
                && other.address == address
                && other.privateKey == privateKey
                && other.clientName == clientName
                && other.flags == flags
    }

    public override fun hashCode(): Int {
        var result = 20
        result = result * 42 + address.hashCode()
        result = result * 42 + privateKey.hashCode()
        result = result * 42 + clientName.hashCode()
        result = result * 42 + flags.hashCode()
        return result
    }

    public override fun toString(): String = buildString {
        appendLine("ClientAuthEntry: [")
        append("    address: ")
        appendLine(address)
        append("    privateKey: ")
        appendLine(privateKey)
        append("    clientName: ")
        appendLine(clientName)

        append("    flags: [")
        if (flags.isEmpty()) {
            append(']')
        } else {
            for (flag in flags) {
                appendLine()
                append("        ")
                append(flag)
            }
            appendLine()
            append("    ]")
        }

        appendLine()
        append(']')
    }
}
