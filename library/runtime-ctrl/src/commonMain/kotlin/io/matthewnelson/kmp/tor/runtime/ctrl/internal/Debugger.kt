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
package io.matthewnelson.kmp.tor.runtime.ctrl.internal

import io.matthewnelson.kmp.tor.runtime.core.ItBlock
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmSynthetic

@JvmInline
internal value class Debugger private constructor(private val notify: Notify) {

    internal companion object {

        @JvmSynthetic
        internal fun of(
            prefix: Any,
            block: ItBlock<String>
        ): Debugger = Debugger(Notify(prefix.toString(), block))

        internal fun Debugger?.d(lazyText: () -> String) {
            if (this == null) return
            notify(lazyText())
        }
    }

    private class Notify(
        private val prefix: String,
        private val delegate: ItBlock<String>,
    ): ItBlock<String> {

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
}
