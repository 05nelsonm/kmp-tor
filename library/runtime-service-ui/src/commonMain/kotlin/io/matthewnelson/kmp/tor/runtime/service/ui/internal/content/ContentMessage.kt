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
package io.matthewnelson.kmp.tor.runtime.service.ui.internal.content

import kotlin.jvm.JvmSynthetic

internal sealed class ContentMessage<T: Any> private constructor(): ContentText<T>() {

    internal sealed class NewNym<T: Any> private constructor(): ContentMessage<T>() {

        internal object Success: NewNym<Unit>() {
            internal override val value: Unit = Unit
        }

        internal sealed class RateLimited<T: Any> private constructor(): NewNym<T>() {

            internal class Raw private constructor(
                internal override val value: String,
            ): RateLimited<String>() {

                internal companion object {

                    @JvmSynthetic
                    internal fun of(value: String) = Raw(value)
                }
            }

            internal class Seconds private constructor(
                internal override val value: Int,
            ): RateLimited<Int>() {

                internal companion object {
                    @JvmSynthetic
                    internal fun of(value: Int) = Seconds(value)
                }
            }

            internal companion object {

                @JvmSynthetic
                internal fun of(line: String): RateLimited<*> {
                    // Rate limiting NEWNYM request: delaying by 10 second(s)
                    val seconds = line.substringBeforeLast(' ', "")
                        .substringAfterLast(' ', "")
                        .toIntOrNull()

                    return if (seconds != null) {
                        Seconds.of(seconds)
                    } else {
                        Raw.of(line)
                    }
                }
            }
        }

        public override fun toString(): String = "NewNym." + when (this) {
            is Success -> "Success"
            is RateLimited.Raw -> "RateLimited.Raw[value=$value]"
            is RateLimited.Seconds -> "RateLimited[seconds=$value]"
        }
    }
}
