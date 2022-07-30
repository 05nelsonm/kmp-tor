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
@file:Suppress("DEPRECATION")

package io.matthewnelson.kmp.tor.common.internal

import io.matthewnelson.kmp.tor.common.address.Scheme

@Suppress("nothing_to_inline")
internal inline fun String.stripString(): String {
    var limit = length

    // Disregard padding and/or whitespace from end of string
    while (limit > 0) {
        val c = this[limit - 1]
        if (c != '=' && c != '\n' && c != '\r' && c != ' ' && c != '\t') {
            break
        }
        limit--
    }

    return this.substring(0, limit).trimStart()
}

@Suppress("nothing_to_inline")
internal inline fun String.separateSchemeFromAddress(): Pair<Scheme?, String> {
    val trimmed = this.trim()
    val scheme: Scheme? = Scheme.fromString(trimmed, trim = false)
    return Pair(
        scheme,
        if (scheme != null) {
            trimmed.substring(scheme.toString().length)
        } else {
            trimmed
        }
    )
}

@Suppress("nothing_to_inline")
internal inline fun String.stripAddress(): String {
    return separateSchemeFromAddress()
        .second
        .substringBefore('.')
}
