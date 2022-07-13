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
package io.matthewnelson.kmp.tor.manager.internal.ext

import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent.Action
import io.matthewnelson.kmp.tor.manager.common.exceptions.InterruptedException
import io.matthewnelson.kmp.tor.manager.internal.actions.ActionProcessor
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlin.jvm.JvmSynthetic

internal inline fun Action.catchInterrupt(crossinline block: (Throwable) -> Unit) =
    CoroutineExceptionHandler { _, throwable ->
        val thrown = throwable.cause?.let { cause ->
            if (cause.message?.contains("$this ${ActionProcessor.INTERRUPT_MESSAGE}") == true) {
                InterruptedException(cause.message, cause)
            } else {
                cause
            }
        } ?: throwable

        block.invoke(thrown)
    }
