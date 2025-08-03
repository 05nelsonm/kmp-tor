/*
 * Copyright (c) 2023 Matthew Nelson
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

package io.matthewnelson.kmp.tor.runtime.core

/**
 * Helper for non-Kotlin consumers instead of using
 *
 *     T.() -> Unit
 *
 * which would force a return of `Unit`.
 *
 * e.g. (Kotlin)
 *
 *     My.Builder {
 *         add(My.Factory) { enable = true }
 *     }
 *
 * e.g. (Java)
 *
 *     My.Builder(b -> {
 *         b.add(My.Factory.Companion, s -> {
 *             s.enable = true;
 *         });
 *     });
 *
 * @see [ItBlock]
 * @see [apply]
 * */
public fun interface ThisBlock<in This: Any> {
    public operator fun This.invoke()
}

/**
 * Helper for non-Kotlin consumers instead of using
 *
 *     (T) -> Unit
 *
 * which would force a return of `Unit`.
 *
 * e.g. (Kotlin)
 *
 *     My.Builder {
 *         it.add(My.Factory) { s -> s.enable = true }
 *     }
 *
 * e.g. (Java)
 *
 *     My.Builder(b -> {
 *         b.add(My.Factory.Companion, s -> {
 *             s.enable = true;
 *         });
 *     });
 *
 * @see [ThisBlock]
 * @see [apply]
 * */
public fun interface ItBlock<in It: Any?> {
    public operator fun invoke(it: It)
}

public inline fun <This: Any> This.apply(block: ThisBlock<This>): This {
    with(block) { invoke() }
    return this
}

public inline fun <It: Any?> It.apply(block: ItBlock<It>): It {
    block(this)
    return this
}
