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
package io.matthewnelson.kmp.tor.runtime.core.internal

import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.core.address.Port
import org.kotlincrypto.endians.BigEndian.Companion.toBigEndian
import kotlin.experimental.ExperimentalNativeApi

internal actual val IsAndroidHost: Boolean get() {
    @OptIn(ExperimentalNativeApi::class)
    return when (Platform.osFamily) {
        OsFamily.ANDROID -> true
        else -> false
    }
}

internal actual val IsDarwinMobile: Boolean get() {
    @OptIn(ExperimentalNativeApi::class)
    return when (Platform.osFamily) {
        OsFamily.IOS,
        OsFamily.TVOS,
        OsFamily.WATCHOS-> true
        else -> false
    }
}

@InternalKmpTorApi
public inline fun Port.toSinPort(): UShort = value.toSinPort()

@PublishedApi
internal fun Int.toSinPort(): UShort {
    @OptIn(ExperimentalNativeApi::class)
    if (!(Platform.isLittleEndian)) return toUShort()

    return toShort()
        .toBigEndian()
        .toLittleEndian()
        .toShort()
        .toUShort()
}
