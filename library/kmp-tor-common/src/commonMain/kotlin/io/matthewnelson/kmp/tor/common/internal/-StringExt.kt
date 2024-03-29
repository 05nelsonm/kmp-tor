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

@Suppress("nothing_to_inline")
internal inline fun String.stripBaseEncoding(): String {
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
internal inline fun String.findHostnameAndPortFromUrl(): String {
    return substringAfter("://") // scheme
        .substringAfter('@') // username:password
        .substringBefore('/') // path
}

@Suppress("nothing_to_inline")
internal inline fun String.findOnionAddressFromUrl(): String {
    return findHostnameAndPortFromUrl()
        .substringBefore(':') // port
        .substringBeforeLast(".onion")
        .substringAfterLast('.') // subdomains
}
