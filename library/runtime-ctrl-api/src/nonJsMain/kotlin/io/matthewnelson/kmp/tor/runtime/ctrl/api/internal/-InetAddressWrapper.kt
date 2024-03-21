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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "KotlinRedundantDiagnosticSuppress")

package io.matthewnelson.kmp.tor.runtime.ctrl.api.internal

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.wrapIOException
import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.IPAddress
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmSynthetic

@JvmInline
internal expect value class InetAddressWrapper private constructor(
    private val value: Any
) {

    @Throws(Exception::class)
    @OptIn(ExperimentalStdlibApi::class)
    internal fun openServerSocket(port: Int): AutoCloseable

    internal companion object {

        @JvmSynthetic
        @Throws(IOException::class)
        internal fun IPAddress.toInetAddressWrapper(): InetAddressWrapper
    }
}

@Throws(IOException::class)
@Suppress("NOTHING_TO_INLINE")
internal inline fun InetAddressWrapper.isPortAvailable(port: Int): Boolean {
    try {
        @OptIn(ExperimentalStdlibApi::class)
        openServerSocket(port).use {}
        return true
    } catch (t: Throwable) {
        // Android will throw NetworkOnMainThreadException here,
        // and if port is invalid an IllegalArgumentException is
        // thrown. So, only check for IOException which indicated
        // that the ServerSocket failed to bind
        if (t is IOException) return false
        throw t.wrapIOException()
    }
}
