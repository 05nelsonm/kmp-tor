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
import io.matthewnelson.kmp.tor.runtime.ctrl.TorCtrl

internal inline fun ItBlock<String>?.asDebugger(): TorCtrl.Debugger? {
    if (this == null) return null
    if (this is TorCtrl.Debugger) return this
    return object : TorCtrl.Debugger(), ItBlock<String> by this {
        // Default on for compatibility
        override fun isEnabled(): Boolean = true
    }
}

internal inline fun TorCtrl.Debugger?.d(lazyText: () -> String) {
    if (this?.isEnabled() != true) return
    invoke(lazyText())
}

@Throws(IllegalArgumentException::class)
internal fun TorCtrl.Debugger.wrap(prefix: Any): TorCtrl.Debugger {
    if (this is DebugWrapper) return this
    return DebugWrapper(this, prefix)
}

private class DebugWrapper(
    private val delegate: TorCtrl.Debugger,
    private val prefix: Any,
): TorCtrl.Debugger() {

    init {
        require(delegate !is DebugWrapper) { "delegate cannot be an instance of DebugWrapper" }
        require(prefix !is TorCtrl.Debugger) { "prefix cannot be an instance of TorCtrl.Debugger" }
    }

    override fun isEnabled(): Boolean = delegate.isEnabled()

    override fun invoke(it: String) {
        try {
            delegate("$prefix $it")
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
