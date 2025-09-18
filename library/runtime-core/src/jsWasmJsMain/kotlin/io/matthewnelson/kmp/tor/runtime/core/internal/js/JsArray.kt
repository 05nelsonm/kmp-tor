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
@file:Suppress("NOTHING_TO_INLINE")

package io.matthewnelson.kmp.tor.runtime.core.internal.js

@JsName("Array")
internal external class JsArray {
    internal val length: Int
}

internal inline fun JsArray.getJsArray(index: Int): JsArray = jsArrayGetJsArray(this, index)
internal inline fun JsArray.getJsObject(index: Int): JsObject = jsArrayGetJsObject(this, index)

internal expect fun jsArrayGetJsArray(array: JsArray, index: Int): JsArray
internal expect fun jsArrayGetJsObject(array: JsArray, index: Int): JsObject
