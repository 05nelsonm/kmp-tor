/*
 * Copyright (c) 2024 Matthew Nelson
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
@file:Suppress("OPTIONAL_DECLARATION_USAGE_IN_NON_COMMON_SOURCE")

package io.matthewnelson.kmp.tor.runtime.service.ui.internal.content

internal sealed class ContentText<T: Any> {

    internal abstract val value: T

    public override fun equals(other: Any?): Boolean {
        if (other !is ContentText<*>) return false
        if (other::class != this::class) return false
        return other.value.toString() == value.toString()
    }

    public override fun hashCode(): Int {
        var result = 17
        result = result * 42 + this::class.hashCode()
        result = result * 42 + value.hashCode()
        return result
    }

    public override fun toString(): String {
        val name = this::class.simpleName ?: "ContentText"

        val value = if (value is Unit) {
            ""
        } else {
            "[value=$value]"
        }

        return "$name$value"
    }
}
