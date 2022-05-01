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
package io.matthewnelson.kmp.tor.controller.common.config

import kotlin.jvm.JvmField

class ConfigEntry {
    @JvmField
    val key: String
    @JvmField
    val value: String
    @JvmField
    val isDefault: Boolean

    constructor(key: String, value: String) {
        this.key = key
        this.value = value
        isDefault = false
    }

    constructor(key: String) {
        this.key = key
        this.value = ""
        this.isDefault = true
    }

    override fun equals(other: Any?): Boolean {
        return  other != null                   &&
                other is ConfigEntry            &&
                other.key == key                &&
                other.value == value            &&
                other.isDefault == isDefault
    }

    override fun hashCode(): Int {
        var result = 17
        result = result * 31 + key.hashCode()
        result = result * 31 + value.hashCode()
        result = result * 31 + isDefault.hashCode()
        return result
    }

    override fun toString(): String {
        return "ConfigEntry(key=$key,value=$value,isDefault=$isDefault)"
    }
}
