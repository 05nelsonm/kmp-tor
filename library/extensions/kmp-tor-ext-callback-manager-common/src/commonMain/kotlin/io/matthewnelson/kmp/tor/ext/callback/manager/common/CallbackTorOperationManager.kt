/*
 * Copyright (c) 2022 Matthew Nelson
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
package io.matthewnelson.kmp.tor.ext.callback.manager.common

import io.matthewnelson.kmp.tor.ext.callback.controller.common.Task
import io.matthewnelson.kmp.tor.ext.callback.controller.common.TorCallback
import io.matthewnelson.kmp.tor.manager.common.TorOperationManager

/**
 * See [TorOperationManager]
 * */
interface CallbackTorOperationManager {
    fun start(failure: TorCallback<Throwable>?, success: TorCallback<Any?>): Task
    fun startQuietly()

    fun restart(failure: TorCallback<Throwable>?, success: TorCallback<Any?>): Task
    fun restartQuietly()

    fun stop(failure: TorCallback<Throwable>?, success: TorCallback<Any?>): Task
    fun stopQuietly()
}
