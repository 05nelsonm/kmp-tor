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
package io.matthewnelson.kmp.tor.common.internal

import io.matthewnelson.encoding.builders.Base16
import io.matthewnelson.encoding.builders.Base32Default
import io.matthewnelson.encoding.builders.Base64
import io.matthewnelson.kmp.tor.common.annotation.InternalTorApi
import kotlin.jvm.JvmSynthetic

@InternalTorApi
object TorStrings {
    @InternalTorApi
    @Suppress("SpellCheckingInspection")
    const val CLRF: String = "\r\n"
    @InternalTorApi
    const val MULTI_LINE_END: Char = '.'
    @InternalTorApi
    const val REDACTED: String = "[REDACTED]"
    @InternalTorApi
    const val SP: String = " "

    @JvmSynthetic
    internal val base16 = Base16 {
        isLenient = true
        lineBreakInterval = 0
        encodeToLowercase = false
    }

    @JvmSynthetic
    internal val base32 = Base32Default {
        isLenient = true
        lineBreakInterval = 0
        encodeToLowercase = false
        padEncoded = true
    }

    @JvmSynthetic
    internal val base64 = Base64 {
        isLenient = true
        lineBreakInterval = 0
        encodeToUrlSafe = false
        padEncoded = true
    }
}
