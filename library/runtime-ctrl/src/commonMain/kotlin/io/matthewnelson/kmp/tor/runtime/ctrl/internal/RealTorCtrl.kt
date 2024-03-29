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

import io.matthewnelson.kmp.tor.runtime.ctrl.TorCtrl
import kotlin.jvm.JvmSynthetic

internal class RealTorCtrl private constructor(
    factory: TorCtrl.Factory,
): AbstractTorCtrl(
    factory.staticTag,
    factory.initialObservers,
    factory.handler,
) {

    override fun destroy() {
        // TODO: disconnect
    }

    override fun startProcessor() {
        // TODO("Not yet implemented")
    }

    // @Throws(UncaughtException::class)
    protected override fun onDestroy(): Boolean {
        return super.onDestroy()
    }

    internal companion object {

        // TODO
        @JvmSynthetic
        internal fun of(
            factory: TorCtrl.Factory,
        ): RealTorCtrl = RealTorCtrl(factory)
    }
}
