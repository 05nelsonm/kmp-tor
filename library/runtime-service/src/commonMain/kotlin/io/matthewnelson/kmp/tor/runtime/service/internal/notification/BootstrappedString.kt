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
package io.matthewnelson.kmp.tor.runtime.service.internal.notification

import kotlin.jvm.JvmSynthetic

internal class BootstrappedString private constructor(
    private val value: String,
): CharSequence by value {

    public override fun equals(other: Any?): Boolean = other is CharSequence && other.toString() == value
    public override fun hashCode(): Int = value.hashCode()
    public override fun toString() = value

    internal companion object {

        @JvmSynthetic
        internal fun of(
            value: String,
        ): BootstrappedString = BootstrappedString(value)
    }
}
