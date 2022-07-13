/*
 * Copyright (c) 2022 Matthew Nelson
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
package io.matthewnelson.kmp.tor.internal

import java.io.File

@Suppress("nothing_to_inline")
internal inline fun File.doesContentMatchExpected(expected: String): Boolean {
    return try {
        if (exists()) {
            if (isFile) {
                readText() == expected
            } else {
                delete()
                false
            }
        } else {
            false
        }
    } catch (e: Exception) {
        false
    }
}
