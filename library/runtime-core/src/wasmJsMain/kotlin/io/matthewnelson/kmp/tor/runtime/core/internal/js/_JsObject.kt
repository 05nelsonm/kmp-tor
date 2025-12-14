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
@file:Suppress("OPT_IN_USAGE")

package io.matthewnelson.kmp.tor.runtime.core.internal.js

internal actual fun jsObjectNew(): JsObject = js("({})")
internal actual fun jsObjectGetBoolean(obj: JsObject, key: String): Boolean = js("obj[key]")
internal actual fun jsObjectGetString(obj: JsObject, key: String): String = js("obj[key]")
internal actual fun jsObjectSetInt(obj: JsObject, key: String, value: Int) { js("obj[key] = value") }
internal actual fun jsObjectSetBoolean(obj: JsObject, key: String, value: Boolean) { js("obj[key] = value") }
internal actual fun jsObjectSetString(obj: JsObject, key: String, value: String) { js("obj[key] = value") }
