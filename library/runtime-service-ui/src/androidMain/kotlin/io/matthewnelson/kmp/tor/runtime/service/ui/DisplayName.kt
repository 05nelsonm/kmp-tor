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
package io.matthewnelson.kmp.tor.runtime.service.ui

import io.matthewnelson.kmp.tor.runtime.FileID
import io.matthewnelson.kmp.tor.runtime.TorRuntime

/**
 * Configuration option types for [KmpTorServiceUI.Config.Builder.displayName].
 *
 * Will be shown below the selector action button icons
 * [R.drawable.ic_kmp_tor_ui_action_previous] and
 * [R.drawable.ic_kmp_tor_ui_action_next]
 *
 * e.g. (The bottom right corner of the notification)
 *
 *                          |
 *             <        >   |      <<< Button icons
 *               TextView   |      <<< Loaded DisplayName
 *     _____________________|
 * */
public sealed class DisplayName private constructor() {

    /**
     * Will display to the user the [FileID.fidEllipses] for that
     * [TorRuntime] currently "selected" in the notification area.
     *
     * e.g.
     *
     *     [abcd…1234]
     * */
    public data object FID: DisplayName()

    /**
     * A string resource id to be retrieved and loaded for that
     * [TorRuntime] currently "selected" in the notification area.
     *
     * **NOTE:** Requirements for the retrieved string value match
     * those expressed in [Text.of]
     * */
    public class StringRes(
        @JvmField
        public val id: Int,
    ): DisplayName()

    /**
     * The raw text to be loaded for that [TorRuntime] currently
     * "selected" in the notification area.
     *
     * Not friendly for multilingual applications, but is an option
     *
     * @see [of]
     * */
    public class Text private constructor(
        @JvmField
        public val text: String,
    ): DisplayName() {

        public companion object {

            /**
             * Creates a new [Text] item
             *
             * @throws [IllegalArgumentException] when:
             *  - [text] is blank or empty
             *  - [text] exceeds 50 characters in length
             *  - [text] has multiple lines
             * */
            @JvmStatic
            @Throws(IllegalArgumentException::class)
            public fun of(text: String): Text {
                require(text.length in 1..50) {
                    "text must be between 1 and 50 characters in length"
                }
                require(text.isNotBlank()) {
                    "text cannot be blank"
                }
                require(text.lines().size == 1) {
                    "text cannot be multiple lines"
                }

                return Text(text)
            }
        }
    }

    public final override fun equals(other: Any?): Boolean {
        if (other !is DisplayName) return false
        return other.hashCode() == hashCode()
    }

    public final override fun hashCode(): Int {
        var result = 17
        result = result * 42 + this::class.hashCode()
        result = result * 42 + toString().hashCode()
        return result
    }

    public final override fun toString(): String = "DisplayName." + when (this) {
        is FID -> "FID"
        is StringRes -> "StringRes[id=$id]"
        is Text -> "Text[text=$text]"
    }
}
