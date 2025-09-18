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
package io.matthewnelson.kmp.tor.runtime.core.internal.node

import io.matthewnelson.kmp.file.Buffer
import io.matthewnelson.kmp.tor.common.api.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.core.Disposable

@InternalKmpTorApi
public actual fun JsSocket.onData(block: (Buffer) -> Unit): Disposable.Once {
    val listener: (buf: dynamic) -> Unit = {
        val buf = Buffer.wrap(it)
        block(buf)
    }
    jsEventEmitterOn(this, "data", listener)
    return Disposable.Once.of {
        jsEventEmitterRemoveListener(this, "data", listener)
    }
}

@InternalKmpTorApi
public actual fun JsSocket.onceClose(block: (Boolean) -> Unit): Disposable.Once {
    jsEventEmitterOnce(this, "close", block)
    return Disposable.Once.of {
        jsEventEmitterRemoveListener(this, "close", block)
    }
}

@InternalKmpTorApi
public actual fun JsSocket.onceDrain(block: () -> Unit): Disposable.Once {
    jsEventEmitterOnce(this, "drain", block)
    return Disposable.Once.of {
        jsEventEmitterRemoveListener(this, "drain", block)
    }
}

@InternalKmpTorApi
public actual fun JsSocket.onceError(block: (Throwable) -> Unit): Disposable.Once {
    jsEventEmitterOnce(this, "error", block)
    return Disposable.Once.of {
        jsEventEmitterRemoveListener(this, "error", block)
    }
}
