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
package io.matthewnelson.kmp.tor.common.address

import io.matthewnelson.component.parcelize.Parcelable

/**
 * Base interface for all kmp-tor [Address]es
 *
 * @see [IPAddress]
 * @see [IPAddressV4]
 * @see [IPAddressV6]
 * @see [OnionAddress]
 * @see [OnionAddressV3]
 * */
sealed interface Address: Parcelable {

    val value: String

    /**
     * Returns the [value] in it's canonicalized hostname format.
     *
     * [IPAddressV4] -> "127.0.0.1"
     * [IPAddressV6] -> "[::1]"
     * [OnionAddressV3] -> "2gzyxa5ihm7nsggfxnu52rck2vv4rvmdlkiu3zzui5du4xyclen53wid.onion"
     * */
    fun canonicalHostname(): String
}
