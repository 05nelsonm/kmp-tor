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
package io.matthewnelson.kmp.tor.runtime.core.config

/**
 * Model of tor's `INTERVAL` type.
 *
 * @see [ConfigureInterval]
 * */
public abstract class IntervalUnit private constructor() {

    public data object SECONDS: IntervalUnit()
    public data object MINUTES: IntervalUnit()
    public data object HOURS: IntervalUnit()
    public data object DAYS: IntervalUnit()
    public data object WEEKS: IntervalUnit()
    public data object MONTHS: IntervalUnit()

    /**
     * Concatenates [num] with lowercase output of [toString]
     * for the given [IntervalUnit].
     *
     * e.g.
     *
     *     IntervalUnit.MINUTES.of(5)
     *     // 5 minutes
     * */
    public fun of(num: Int): String = "$num ${toString().lowercase()}"
}
