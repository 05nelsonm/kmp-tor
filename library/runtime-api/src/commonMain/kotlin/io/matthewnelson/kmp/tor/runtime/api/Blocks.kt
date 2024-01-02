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
@file:Suppress("KotlinRedundantDiagnosticSuppress")

package io.matthewnelson.kmp.tor.runtime.api

// TODO: Move to core-api
/**
 * Helper for non-Kotlin consumers instead of using
 * `T.() -> Unit` which would force a return of `Unit`.
 *
 * e.g.
 *
 *     // Java
 *     My.Builder(b -> {
 *         b.add(My.Factory.Companion, s -> {
 *             s.enable = true;
 *         });
 *     });
 *
 *     // Kotlin
 *     My.Builder {
 *         add(My.Factory) {
 *             enable = true
 *         }
 *     }
 *
 * @see [ItBlock]
 * @see [apply]
 * */
public fun interface ThisBlock<in T: Any> {
    public operator fun T.invoke()
}

/**
 * Helper for non-Kotlin consumers instead of using
 * `(T) -> Unit` which would force a return of `Unit`.
 *
 * e.g.
 *
 *     // Java
 *     My.Builder(b -> {
 *         b.add(My.Factory.Companion, s -> {
 *             s.enable = true;
 *         });
 *     });
 *
 *     // Kotlin
 *     My.Builder {
 *         it.add(My.Factory) { s ->
 *             s.enable = true
 *         }
 *     }
 *
 * @see [ThisBlock]
 * @see [apply]
 * */
public fun interface ItBlock<in T: Any> {
    public operator fun invoke(it: T)
}

@Suppress("NOTHING_TO_INLINE")
public inline fun <T: Any> T.apply(block: ThisBlock<T>): T {
    with(block) { invoke() }
    return this
}

@Suppress("NOTHING_TO_INLINE")
public inline fun <T: Any> T.apply(block: ItBlock<T>): T {
    block(this)
    return this
}
