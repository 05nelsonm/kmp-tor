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
package io.matthewnelson.kmp.tor.runtime.internal

import io.matthewnelson.kmp.file.File

internal class EnvironmentKey internal constructor(internal val work: File, internal val cache: File) {

    override fun equals(other: Any?): Boolean {
        if (other !is EnvironmentKey) return false
        if (other.work == work) return true
        if (other.work == cache) return true
        if (other.cache == work) return true
        if (other.cache == cache) return true
        return false
    }

    override fun hashCode(): Int {
        var result = 17
        result = result * 42 + work.hashCode()
        result = result * 42 + cache.hashCode()
        return result
    }
}
