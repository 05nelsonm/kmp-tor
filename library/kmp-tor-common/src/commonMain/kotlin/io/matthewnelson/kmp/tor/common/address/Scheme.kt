/*
 * Copyright (c) 2021 Matthew Nelson
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

import io.matthewnelson.component.parcelize.Parcelable
import io.matthewnelson.component.parcelize.Parcelize
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

@Deprecated(
    message = """
        This class was initially offered as a convenience in conjunction
        with OnionUrl. As OnionUrl has been deprecated for its many issues,
        and this class not being utilized by inheriting module APIs, it was
        deemed unnecessary to maintain and should not be used.
        
        For more info, see: https://github.com/05nelsonm/kmp-tor/issues/235
    """
)
@Parcelize
enum class Scheme: Parcelable {
    HTTP,
    HTTPS,
    WS,
    WSS;

    override fun toString(): String {
        return when (this) {
            HTTP -> "$SCHEME_HTTP://"
            HTTPS -> "$SCHEME_HTTPS://"
            WS -> "$SCHEME_WS://"
            WSS -> "$SCHEME_WSS://"
        }
    }

    companion object {
        const val SCHEME_HTTP = "http"
        const val SCHEME_HTTPS = "https"
        const val SCHEME_WS = "ws"
        const val SCHEME_WSS = "wss"

        @JvmStatic
        @JvmOverloads
        @Suppress("DEPRECATION")
        fun fromString(string: String, trim: Boolean = true): Scheme? {
            val trimmed = if (trim) string.trim() else string
            return when {
                trimmed.startsWith(SCHEME_HTTPS) -> HTTPS
                trimmed.startsWith(SCHEME_HTTP) -> HTTP
                trimmed.startsWith(SCHEME_WSS) -> WSS
                trimmed.startsWith(SCHEME_WS) -> WS
                else -> null
            }
        }
    }
}
