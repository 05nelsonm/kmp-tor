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
@file:Suppress("NOTHING_TO_INLINE", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package io.matthewnelson.kmp.tor.runtime.core.internal.node

import io.matthewnelson.kmp.tor.common.api.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.core.Disposable

@InternalKmpTorApi
public actual fun JsEventEmitter.onClose(block: () -> Unit): Disposable.Once {
    jsEventEmitterOn(this, "close", block)
    return Disposable.Once.of {
        jsEventEmitterRemoveListener(this, "close", block)
    }
}

@InternalKmpTorApi
public actual fun JsEventEmitter.onError(block: (Throwable) -> Unit): Disposable.Once {
    jsEventEmitterOn(this, "error", block)
    return Disposable.Once.of {
        jsEventEmitterRemoveListener(this, "error", block)
    }
}

@OptIn(InternalKmpTorApi::class)
internal inline fun jsEventEmitterOn(
    emitter: JsEventEmitter,
    event: String,
    listener: Function<*>,
) {
    js("emitter.on(event, listener)")
}

@OptIn(InternalKmpTorApi::class)
internal inline fun jsEventEmitterOnce(
    emitter: JsEventEmitter,
    event: String,
    listener: Function<*>,
) {
    js("emitter.once(event, listener)")
}

@OptIn(InternalKmpTorApi::class)
internal inline fun jsEventEmitterRemoveListener(
    emitter: JsEventEmitter,
    event: String,
    listener: Function<*>,
) {
    js("emitter.removeListener(event, listener)")
}
