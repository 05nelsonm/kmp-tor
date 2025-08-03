/*
 * Copyright (c) 2025 Matthew Nelson
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
@file:Suppress("NOTHING_TO_INLINE")

package io.matthewnelson.kmp.tor.runtime.core.internal

import io.matthewnelson.kmp.file.ANDROID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.EmptyCoroutineContext

private val SkikoUIDispatcher: CoroutineDispatcher? by lazy {
    try {
        @Suppress("UNNECESSARY_SAFE_CALL")
        val dispatcher = Class.forName("org.jetbrains.skiko.MainUIDispatcher_awtKt")
            ?.getMethod("getMainUIDispatcher")
            ?.invoke(null) as? CoroutineDispatcher

        // Shouldn't throw here, but just to be 100% certain...
        dispatcher?.isDispatchNeeded(EmptyCoroutineContext)

        dispatcher
    } catch (_: Throwable) {
        null
    }
}

internal inline fun isComposeDesktopApplication(): Boolean {
    if (ANDROID.SDK_INT != null) return false

    // Compose desktop configures this property. Check for its existence before
    // going to reflection based retrieval of Skiko's MainUIDispatcher.
    return System.getProperty("compose.application.configure.swing.globals") != null
}

internal actual inline fun Dispatchers.isUIDispatcherAvailable(): Boolean {
    if (isComposeDesktopApplication() && SkikoUIDispatcher != null) return true

    return try {
        Main.isDispatchNeeded(EmptyCoroutineContext)
        true
    } catch (_: Throwable) {
        false
    }
}

internal actual inline fun Dispatchers.composeDesktopUIDispatcherOrNull(): CoroutineDispatcher? {
    if (!isComposeDesktopApplication()) return null
    return SkikoUIDispatcher
}
