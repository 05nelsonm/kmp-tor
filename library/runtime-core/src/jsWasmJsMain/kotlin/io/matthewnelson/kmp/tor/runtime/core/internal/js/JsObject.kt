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
@file:Suppress("NOTHING_TO_INLINE", "UNUSED")

package io.matthewnelson.kmp.tor.runtime.core.internal.js

@JsName("Object")
internal external class JsObject {
    internal companion object {
        internal fun values(obj: JsObject): JsArray
    }
}

internal inline fun JsObject.Companion.new(): JsObject = jsObjectNew()

internal inline fun JsObject.getBoolean(key: String): Boolean = jsObjectGetBoolean(this, key)
internal inline fun JsObject.getString(key: String): String = jsObjectGetString(this, key)

internal inline operator fun JsObject.set(key: String, value: Int) { jsObjectSetInt(this, key, value) }
internal inline operator fun JsObject.set(key: String, value: Boolean) { jsObjectSetBoolean(this, key, value) }
internal inline operator fun JsObject.set(key: String, value: String) { jsObjectSetString(this, key, value) }

internal expect fun jsObjectNew(): JsObject

internal expect fun jsObjectGetBoolean(obj: JsObject, key: String): Boolean
internal expect fun jsObjectGetString(obj: JsObject, key: String): String

internal expect fun jsObjectSetInt(obj: JsObject, key: String, value: Int)
internal expect fun jsObjectSetBoolean(obj: JsObject, key: String, value: Boolean)
internal expect fun jsObjectSetString(obj: JsObject, key: String, value: String)
