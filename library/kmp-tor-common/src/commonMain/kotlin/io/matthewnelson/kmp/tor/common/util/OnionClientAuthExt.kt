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
package io.matthewnelson.kmp.tor.common.util

import io.matthewnelson.kmp.tor.common.address.OnionAddressV3
import io.matthewnelson.kmp.tor.common.clientauth.OnionClientAuth
import kotlin.jvm.JvmSynthetic

@JvmSynthetic
@Suppress("nothing_to_inline")
internal inline fun OnionClientAuth.PrivateKey.descriptorString(address: OnionAddressV3): String =
    StringBuilder(address.value)
        .append(':')
        .append(keyType)
        .append(':')
        .append(value)
        .toString()

@JvmSynthetic
@Suppress("nothing_to_inline")
internal inline fun OnionClientAuth.PublicKey.descriptorString(): String =
    StringBuilder(OnionClientAuth.PublicKey.DESCRIPTOR)
        .append(':')
        .append(keyType)
        .append(':')
        .append(value)
        .toString()
