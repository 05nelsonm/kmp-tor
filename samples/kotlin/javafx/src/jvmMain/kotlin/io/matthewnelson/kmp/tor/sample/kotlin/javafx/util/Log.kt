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
package io.matthewnelson.kmp.tor.sample.kotlin.javafx.util

import java.text.SimpleDateFormat
import java.util.*

object Log {
    fun d(tag: String, message: String) {
        println("${time()} D/$tag: $message")
    }

    fun e(tag: String, cause: Throwable) {
        println("${time()} E/$tag: ${cause.stackTraceToString()}")
    }

    fun w(tag: String, message: String, cause: Throwable? = null) {
        val print = "${time()} W/$tag: $message" + if (cause != null) {
            "\n${cause.stackTraceToString()}"
        } else {
            ""
        }

        println(print)
    }

    private const val PATTERN = "yyyy-MM-dd HH:mm:ss"
    private fun time(): String {
        val format = SimpleDateFormat(PATTERN, Locale.US)
        return format.format(Date())
    }

}