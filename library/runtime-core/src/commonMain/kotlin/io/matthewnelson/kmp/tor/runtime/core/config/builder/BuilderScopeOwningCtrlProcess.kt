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
@file:Suppress("ConvertSecondaryConstructorToPrimary")

package io.matthewnelson.kmp.tor.runtime.core.config.builder

import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.tor.common.api.KmpTorDsl
import io.matthewnelson.kmp.tor.runtime.core.config.TorOption
import io.matthewnelson.kmp.tor.runtime.core.config.TorSetting
import kotlin.jvm.JvmSynthetic

/**
 * A DSL builder scope for configuring [TorOption.__OwningControllerProcess].
 *
 * By default, the [argument] is set to [Process.Current.pid] upon instantiation.
 *
 * @see [TorOption.__OwningControllerProcess.asSetting]
 * */
@KmpTorDsl
public class BuilderScopeOwningCtrlProcess: TorSetting.BuilderScope {

    private constructor(): super(TorOption.__OwningControllerProcess, INIT) {
        argument = Process.Current.pid().toString()
    }

    /**
     * Sets the [argument] to the provided [id].
     *
     * **NOTE:** Setting an invalid process id will cause tor to error out.
     * The [argument] has **already** been set to [Process.Current.pid]
     * when the builder scope was instantiated. You *can* change it though.
     * */
    @KmpTorDsl
    public fun processId(
        id: Int,
    ): BuilderScopeOwningCtrlProcess {
        argument = id.toString()
        return this
    }

    internal companion object {

        @JvmSynthetic
        internal fun get(): BuilderScopeOwningCtrlProcess {
            return BuilderScopeOwningCtrlProcess()
        }
    }
}
