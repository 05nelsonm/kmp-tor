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

import io.matthewnelson.kmp.tor.common.annotation.SealedValueClass
import io.matthewnelson.kmp.tor.controller.common.internal.fsSeparator
import kotlin.jvm.JvmField
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * Denotes a string as being a path to a file or directory. This is
 * resolved to the given platform's file system.
 *
 * @see [Builder]
 * @see [RealPath]
 * */
@SealedValueClass
sealed interface Path {

    val value: String

    /**
     * Builds off of the current [value] using
     * the default [fsSeparator] character.
     * */
    fun builder(): Builder

    /**
     * Builds off of the current [value] using
     * the defined [separator] character.
     * */
    fun builder(separator: Char): Builder

    /**
     * Builds off of the current [value] using
     * the default [fsSeparator] character.
     * */
    fun builder(block: Builder.() -> Builder): Path

    /**
     * Builds off of the current [value] using
     * the defined [separator] character.
     * */
    fun builder(separator: Char, block: Builder.() -> Builder): Path

    fun segments(separator: Char): List<String>

    companion object {
        @JvmStatic
        val fsSeparator: Char get() = fsSeparator()

        @JvmStatic
        operator fun invoke(path: String): Path {
            return RealPath(path)
        }
    }

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
    class Builder @JvmOverloads constructor(@JvmField val separator: Char = fsSeparator()) {

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

        fun build(): Path = RealPath(sb.toString())

        companion object {
            @JvmStatic
            operator fun invoke(block: Builder.() -> Builder): Path =
                block.invoke(Builder()).build()
            @JvmStatic
            operator fun invoke(separator: Char, block: Builder.() -> Builder): Path =
                block.invoke(Builder(separator)).build()
        }
    }
}

@JvmInline
private value class RealPath(override val value: String): Path {

    override fun builder(): Path.Builder {
        return Path.Builder().addSegment(value)
    }

    override fun builder(separator: Char): Path.Builder {
        return Path.Builder(separator).addSegment(value)
    }

    override fun builder(block: Path.Builder.() -> Path.Builder): Path {
        return block.invoke(builder()).build()
    }

    override fun builder(separator: Char, block: Path.Builder.() -> Path.Builder): Path {
        return block.invoke(builder(separator)).build()
    }

    override fun segments(separator: Char): List<String> {
        return value.split(separator)
    }
}
