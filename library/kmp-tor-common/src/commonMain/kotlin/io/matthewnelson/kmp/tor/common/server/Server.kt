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

import io.matthewnelson.component.base64.Base64
import io.matthewnelson.component.encoding.base16.decodeBase16ToArray
import io.matthewnelson.kmp.tor.common.annotation.ExperimentalTorApi
import io.matthewnelson.kmp.tor.common.annotation.SealedValueClass
import kotlin.jvm.JvmField
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmStatic

/**
 * https://torproject.gitlab.io/torspec/control-spec/#general-use-tokens
 * */
class Server private constructor() {

    /**
     * Holder for a valid base16 encoded Server Fingerprint
     *
     * Ex: F00EC2E0A2CA79A57FE7A0918A087987747D772D
     * */
    @SealedValueClass
    @OptIn(ExperimentalTorApi::class)
    sealed interface Fingerprint {
        val value: String
        val valueWithPrefix: String

        fun decode(): ByteArray

        companion object {
            @get:JvmStatic
            val REGEX: Regex get() = "[A-F0-9]{40}".toRegex()

            const val PREFIX = '$'

            @JvmStatic
            @Throws(IllegalArgumentException::class)
            operator fun invoke(value: String): Fingerprint {
                return if (value.firstOrNull() == PREFIX) {
                    RealFingerprint(value.drop(1))
                } else {
                    RealFingerprint(value)
                }
            }

            @JvmStatic
            fun fromStringOrNull(value: String): Fingerprint? {
                return try {
                    invoke(value)
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
        }
    }

    @JvmInline
    private value class RealFingerprint(override val value: String): Fingerprint {

        init {
            require(value.matches(Fingerprint.REGEX)) {
                "$value is not a valid ServerSpec.Fingerprint"
            }
        }

        override val valueWithPrefix: String get() = "${Fingerprint.PREFIX}$value"

        override fun decode(): ByteArray {
            return value.decodeBase16ToArray()!!
        }

        override fun toString(): String {
            return "Fingerprint(value=$value)"
        }
    }

    /**
     * Holder for a Server Nickname
     *
     * Must be between 1 and 19 characters, and contain only characters
     * defined by [REGEX].
     * */
    @SealedValueClass
    @OptIn(ExperimentalTorApi::class)
    sealed interface Nickname {
        val value: String

        companion object {
            @get:JvmStatic
            val REGEX: Regex get() = "[${Base64.Default.CHARS.dropLast(2)}]{1,19}".toRegex()

            @JvmStatic
            @Throws(IllegalArgumentException::class)
            operator fun invoke(value: String): Nickname {
                return RealNickname(value)
            }

            @JvmStatic
            fun fromStringOrNull(value: String): Nickname? {
                return try {
                    RealNickname(value)
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
        }
    }

    @JvmInline
    private value class RealNickname(override val value: String): Nickname {

        init {
            require(value.matches(Nickname.REGEX)) {
                "$value is not a valid Server.Nickname"
            }
        }

        override fun toString(): String {
            return "Nickname(value=$value)"
        }
    }

    data class LongName(
        @JvmField
        val fingerprint: Fingerprint,
        @JvmField
        val nickname: Nickname?,
    ) {

        override fun toString(): String {
            return if (nickname != null) {
                "${fingerprint.valueWithPrefix}$DELIMITER${nickname.value}"
            } else {
                fingerprint.valueWithPrefix
            }
        }

        companion object {
            const val DELIMITER = '~'

            @JvmStatic
            @Throws(IllegalArgumentException::class)
            fun fromString(value: String): LongName {
                val delimiter: Char? = if (value.contains(DELIMITER)) {
                    DELIMITER

                // Check for non-preferred delimiter (clients older than 0.3.1.3-alpha)
                } else if (value.contains('=')) {
                    '='
                } else {
                    null
                }

                return if (delimiter == null) {
                    LongName(
                        fingerprint = Fingerprint(value),
                        nickname = null,
                    )
                } else {
                    LongName(
                        fingerprint = Fingerprint(value.substringBefore(delimiter)),
                        nickname = Nickname(value.substringAfter(delimiter)),
                    )
                }
            }

            @JvmStatic
            fun fromStringOrNull(value: String): LongName? {
                return try {
                    fromString(value)
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
        }
    }
}
