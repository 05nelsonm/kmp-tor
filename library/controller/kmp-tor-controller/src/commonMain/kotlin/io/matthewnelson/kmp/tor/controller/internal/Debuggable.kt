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
package io.matthewnelson.kmp.tor.controller.internal

import io.matthewnelson.kmp.tor.common.annotation.InternalTorApi
import io.matthewnelson.kmp.tor.controller.common.events.TorEvent
import kotlin.jvm.JvmInline

@InternalTorApi
interface Debuggable {
    fun setDebugger(debugger: ((DebugItem) -> Unit)?)
    val hasDebugger: Boolean
}

@InternalTorApi
sealed interface DebugItem {
    fun print(): String

    @JvmInline
    @InternalTorApi
    value class Message(val value: String): DebugItem {
        override fun print(): String = value
    }

    @JvmInline
    @InternalTorApi
    value class Error(val value: Throwable): DebugItem {
        override fun print(): String = value.stackTraceToString()
    }

    @InternalTorApi
    data class ListenerError(val listener: TorEvent.SealedListener, val error: Error): DebugItem {
        override fun print(): String {
            return """
                ListenerError(
                    listener=${listener::class.simpleName ?: "Unknown"}@${listener.hashCode()},
                    error=${error.print()}
                )
            """.trimIndent()
        }
    }
}
