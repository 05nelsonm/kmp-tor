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
package io.matthewnelson.kmp.tor.manager.internal.ext

import io.matthewnelson.kmp.tor.common.annotation.InternalTorApi
import kotlin.jvm.JvmSynthetic

// NOTICE BOOTSTRAP PROGRESS=100 TAG=<some tag> SUMMARY="<some summary>"
@InternalTorApi
@Suppress("nothing_to_inline")
@Throws(IndexOutOfBoundsException::class, NumberFormatException::class)
inline fun String.infoGetBootstrapProgress(): Int =
    split(' ')
    .elementAt(2)
    .split('=')
    .elementAt(1)
    .toInt()

@InternalTorApi
@Suppress("nothing_to_inline")
inline fun String.infoGetBootstrapProgressOrNull(): Int? =
    try {
        infoGetBootstrapProgress()
    } catch (_: Exception) {
        null
    }

// Bootstrapped 10% (conn_done): Connected to a relay
@InternalTorApi
@Suppress("nothing_to_inline")
@Throws(IndexOutOfBoundsException::class, NumberFormatException::class)
inline fun String.eventNoticeBootstrapProgress(): Int =
    split(' ')
    .elementAt(1)
    .dropLast(1)
    .toInt()

@InternalTorApi
@Suppress("nothing_to_inline")
inline fun String.eventNoticeBootstrapProgressOrNull(): Int? =
    try {
        eventNoticeBootstrapProgress()
    } catch (_: Exception) {
        null
    }
