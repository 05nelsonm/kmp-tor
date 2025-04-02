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
@file:Suppress("KotlinRedundantDiagnosticSuppress", "NOTHING_TO_INLINE")

package io.matthewnelson.kmp.tor.runtime.ctrl.internal

import io.matthewnelson.kmp.tor.runtime.core.ItBlock
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException
import io.matthewnelson.kmp.tor.runtime.ctrl.TorCtrl.Debugger

@PublishedApi
internal inline fun ItBlock<String>.commonAsDebugger(
    crossinline isEnabled: () -> Boolean,
): Debugger = object : Debugger() {
    override fun isEnabled(): Boolean = isEnabled.invoke()
    override fun invoke(log: String) { this@commonAsDebugger(log) }
}

internal inline fun Debugger?.d(lazyText: () -> String) {
    if (this?.isEnabled() != true) return
    invoke(lazyText())
}

@Throws(IllegalArgumentException::class)
internal fun Debugger.wrap(prefix: Any): Debugger {
    if (this is DebugWrapper) return this
    return DebugWrapper(this, prefix)
}

private class DebugWrapper(
    private val delegate: Debugger,
    private val prefix: Any,
): Debugger() {

    init {
        require(delegate !is DebugWrapper) { "delegate cannot be an instance of DebugWrapper" }
        require(prefix !is Debugger) { "prefix cannot be an instance of TorCtrl.Debugger" }
    }

    override fun isEnabled(): Boolean = delegate.isEnabled()

    override fun invoke(log: String) {
        try {
            delegate.invoke(log = "$prefix $log")
        } catch (t: Throwable) {
            if (t !is UncaughtException) return

            // If the debug log is being piped to
            // TorRuntime RuntimeEvent.LOG.DEBUG
            // and that observer throws exception,
            // we want to ensure that it throws in
            // order to pipe to RuntimeEvent.ERROR
            throw t
        }
    }
}
