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
package io.matthewnelson.kmp.tor.controller.internal.controller

import io.matthewnelson.kmp.tor.common.annotation.InternalTorApi

@InternalTorApi
sealed class ReplyLine {

    abstract val status: String

    data class SingleLine(
        override val status: String,
        val message: String
    ): ReplyLine() {
        inline val isCommandResponseStatusSuccess: Boolean get() = status.startsWith("25")
        inline val isEventStatusSuccess: Boolean get() = status == "650" && message == "OK"
    }

    data class MultiLine(
        override val status: String,
        val event: String,
        val messages: List<String>
    ): ReplyLine()
}
