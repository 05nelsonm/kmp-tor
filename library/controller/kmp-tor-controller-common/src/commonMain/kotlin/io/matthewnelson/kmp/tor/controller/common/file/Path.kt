/*
 * Copyright (c) 2021 Matthew Nelson
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
package io.matthewnelson.kmp.tor.controller.common.file

import io.matthewnelson.kmp.tor.controller.common.internal.fsSeparator
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmOverloads

/**
 * Denotes a string as being a path to a file or directory. This is
 * resolved to the given platform's file system.
 *
 * @see [Builder]
 * */
@JvmInline
value class Path(val value: String) {

    /**
     * Builds off of the current [Path.value]
     * */
    @JvmOverloads
    fun builder(separator: Char = fsSeparator()): Builder =
        Builder(separator).addSegment(value)

    /**
     * Builds off of the current [Path.value]
     * */
    fun builder(block: Builder.() -> Builder): Path =
        block.invoke(builder()).build()

    /**
     * Builds off of the current [Path.value]
     * */
    fun builder(separator: Char, block: Builder.() -> Builder): Path =
        block.invoke(builder(separator)).build()

    @JvmOverloads
    fun segments(separator: Char = fsSeparator()): List<String> =
        value.split(separator)

    /**
     * Build a new [Path].
     *
     * Note that limitations exist with the initial separator character
     * due to Windows file systems:
     *  - Unix: /tmp/my_tmp_dir/my_tmp_file.txt
     *  - Windows: C:\\...
     *  - Windows: D:\\...
     *
     * Therefore, the initial separator or drive path must be explicitly
     * added. The specified [separator] will automatically be added after
     * the first call to [addSegment].
     *
     * val path: Path = Path.Builder {
     *     addSegment(separator)
     *     addSegment("io")
     *     addSegment("matthewnelson")
     *     addSegment("kmp")
     *     addSegment("tor")
     * }
     *
     * init {
     *     println(path)
     *     // Path(value=/io/matthewnelson/kmp/tor)
     *
     *     println("${path.builder { addSegment("controller") }}")
     *     // Path(value=/io/matthewnelson/kmp/tor/controller)
     * }
     * */
    class Builder @JvmOverloads constructor(val separator: Char = fsSeparator()) {

        private val sb = StringBuilder()
        private var lastSegment: String? = null

        fun addSegment(segment: Char): Builder = addSegment("" + segment)

        fun addSegment(segment: String): Builder = apply {
            val trimmed = segment.trim()
            when {
                trimmed.isEmpty() -> return@apply

                // do not check for fsSeparator on the first call to
                // Builder.addSegment
                sb.isEmpty() -> trimmed

                lastSegment?.lastOrNull() == separator -> {
                    if (trimmed.first() == separator) {
                        trimmed.drop(1)
                    } else {
                        trimmed
                    }
                }
                trimmed.first() == separator -> trimmed
                else -> separator + trimmed
            }.let { formatted ->
                sb.append(formatted)
                lastSegment = formatted
            }
        }

        fun build(): Path = Path(sb.toString())

        companion object {
            operator fun invoke(block: Builder.() -> Builder): Path =
                block.invoke(Builder()).build()
            operator fun invoke(separator: Char, block: Builder.() -> Builder): Path =
                block.invoke(Builder(separator)).build()
        }
    }
}
