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
@file:Suppress("NOTHING_TO_INLINE", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "OPT_IN_USAGE")

package io.matthewnelson.kmp.tor.runtime.core.internal.node

import io.matthewnelson.kmp.tor.common.api.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.core.Disposable

/** @suppress */
@InternalKmpTorApi
public actual fun JsEventEmitter.onError(block: (Throwable) -> Unit): Disposable.Once {
    val listener = jsErrorListener(block)
    jsEventEmitterOn(this, "error", listener)
    return Disposable.Once.of {
        jsEventEmitterRemoveListener(this, "error", listener)
    }
}

internal inline fun jsErrorListener(
    noinline block: (Throwable) -> Unit,
): (JsAny) -> Unit = { jsError ->
    val t = jsError.toThrowableOrNull()
    if (t != null) block(t)
}

@OptIn(InternalKmpTorApi::class)
internal fun jsEventEmitterOn(
    emitter: JsEventEmitter,
    event: String,
    listener: () -> Unit,
) {
    js("emitter.on(event, listener)")
}

@OptIn(InternalKmpTorApi::class)
internal fun jsEventEmitterOn(
    emitter: JsEventEmitter,
    event: String,
    listener: (JsAny) -> Unit,
) {
    js("emitter.on(event, listener)")
}

@OptIn(InternalKmpTorApi::class)
internal fun jsEventEmitterOnce(
    emitter: JsEventEmitter,
    event: String,
    listener: () -> Unit,
) {
    js("emitter.once(event, listener)")
}

@OptIn(InternalKmpTorApi::class)
internal fun jsEventEmitterOnce(
    emitter: JsEventEmitter,
    event: String,
    listener: (Boolean) -> Unit,
) {
    js("emitter.once(event, listener)")
}

@OptIn(InternalKmpTorApi::class)
internal fun jsEventEmitterOnce(
    emitter: JsEventEmitter,
    event: String,
    listener: (JsAny) -> Unit,
) {
    js("emitter.once(event, listener)")
}

@OptIn(InternalKmpTorApi::class)
internal fun jsEventEmitterRemoveListener(
    emitter: JsEventEmitter,
    event: String,
    listener: () -> Unit,
) {
    js("emitter.removeListener(event, listener)")
}

@OptIn(InternalKmpTorApi::class)
internal fun jsEventEmitterRemoveListener(
    emitter: JsEventEmitter,
    event: String,
    listener: (Boolean) -> Unit,
) {
    js("emitter.removeListener(event, listener)")
}

@OptIn(InternalKmpTorApi::class)
internal fun jsEventEmitterRemoveListener(
    emitter: JsEventEmitter,
    event: String,
    listener: (JsAny) -> Unit,
) {
    js("emitter.removeListener(event, listener)")
}
