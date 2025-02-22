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

package io.matthewnelson.kmp.tor.runtime.service.ui.internal

import io.matthewnelson.kmp.tor.runtime.TorState

internal sealed class Progress {

    internal class Determinant private constructor(internal val value: Byte): Progress() {

        internal constructor(state: TorState.Daemon): this(state.bootstrap)

        public override fun equals(other: Any?): Boolean {
            return  other is Determinant
                    && other.value == value
        }

        public override fun hashCode(): Int {
            var result = 12
            result = result * 42 + this::class.hashCode()
            result = result * 42 + value.hashCode()
            return result
        }
    }

    internal data object Indeterminate: Progress()
    internal data object None: Progress()

    public final override fun toString(): String = "Progress." + when (this) {
        is Determinant -> "Determinant{$value}"
        is Indeterminate -> "Indeterminate"
        is None -> "None"
    }
}
