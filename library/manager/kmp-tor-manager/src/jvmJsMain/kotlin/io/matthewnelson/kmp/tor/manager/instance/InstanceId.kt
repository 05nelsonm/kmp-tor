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
package io.matthewnelson.kmp.tor.manager.instance

import io.matthewnelson.kmp.tor.common.annotation.ExperimentalTorApi
import io.matthewnelson.kmp.tor.common.annotation.SealedValueClass
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmStatic

@SealedValueClass
@OptIn(ExperimentalTorApi::class)
sealed interface InstanceId {
    val value: String

    companion object {
        @JvmStatic
        @Throws(IllegalArgumentException::class)
        operator fun invoke(value: String): InstanceId {
            return RealInstanceId(value)
        }
    }
}

@JvmInline
private value class RealInstanceId(override val value: String): InstanceId {
    init {
        require(value.isNotBlank()) {
            "InstanceId.value cannot be blank"
        }
    }

    override fun toString(): String {
        return "InstanceId(value=$value)"
    }
}
