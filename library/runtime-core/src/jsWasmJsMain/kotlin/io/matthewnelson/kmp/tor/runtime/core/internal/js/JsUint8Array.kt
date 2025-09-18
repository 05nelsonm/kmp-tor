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
@file:Suppress("UNUSED")

package io.matthewnelson.kmp.tor.runtime.core.internal.js

import io.matthewnelson.kmp.tor.common.api.InternalKmpTorApi
import kotlin.js.JsName

@InternalKmpTorApi
@JsName("Uint8Array")
public external class JsUint8Array(length: Int) {
    public val byteLength: Int
}

@InternalKmpTorApi
public operator fun JsUint8Array.get(index: Int): Byte = jsArrayGet(this, index)
@InternalKmpTorApi
public operator fun JsUint8Array.set(index: Int, value: Byte) { jsArraySet(this, index, value) }

@OptIn(InternalKmpTorApi::class)
internal expect fun jsArrayGet(array: JsUint8Array, index: Int): Byte
@OptIn(InternalKmpTorApi::class)
internal expect fun jsArraySet(array: JsUint8Array, index: Int, value: Byte)
