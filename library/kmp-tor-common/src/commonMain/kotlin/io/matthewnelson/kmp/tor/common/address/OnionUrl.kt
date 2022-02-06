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
package io.matthewnelson.kmp.tor.common.address

import kotlin.jvm.JvmStatic

/**
 * Helper class for producing and/or storing a valid onion url's components
 *
 * [OnionUrl.toString] results in a valid onion url produced.
 *
 * Ex:
 * ```
 * val onionUrl = OnionUrl(
 *     address=OnionAddressV3(6yxtsbpn2k7exxiarcbiet3fsr4komissliojxjlvl7iytacrnvz2uyd),
 *     path="/some/path",
 *     port=Port(1024),
 *     scheme=Scheme.WSS
 * )
 *
 * println(onionUrl.toString())
 * // output >>> wss://6yxtsbpn2k7exxiarcbiet3fsr4komissliojxjlvl7iytacrnvz2uyd.onion:1024/some/path
 *
 * println(onionUrl.copy(scheme=Scheme.HTTP).toString())
 * // output >>> http://6yxtsbpn2k7exxiarcbiet3fsr4komissliojxjlvl7iytacrnvz2uyd.onion:1024/some/path
 *
 * println(onionUrl.copy(path="", scheme=Scheme.HTTPS).toString())
 * // output >>> https://6yxtsbpn2k7exxiarcbiet3fsr4komissliojxjlvl7iytacrnvz2uyd.onion:1024
 *
 * println(onionUrl.copy(port=null).toString())
 * // output >>> wss://6yxtsbpn2k7exxiarcbiet3fsr4komissliojxjlvl7iytacrnvz2uyd.onion/some/path
 * ```
 * */
data class OnionUrl(
    val address: OnionAddress,
    val path: String = "",
    val port: Port? = null,
    val scheme: Scheme = Scheme.HTTP,
) {

    override fun toString(): String {
        return StringBuilder().let { sb ->
            sb.append(scheme)
            sb.append(address.valueDotOnion)
            if (port != null) {
                sb.append(':').append(port.value)
            }
            if (path.isNotEmpty() && path.first() != '/') {
                sb.append('/')
            }
            sb.append(path)
            sb.toString()
        }
    }

    companion object {

        /**
         * Parses a URL and breaks it into subcomponents.
         *
         * @throws [IllegalArgumentException] when:
         *  - malformed url
         *  - address is not a valid [OnionAddressV3]
         *  - port is not a valid [Port]
         * */
        @JvmStatic
        @Throws(IllegalArgumentException::class)
        fun fromString(url: String): OnionUrl {
            val (scheme, substring) = url.separateSchemeFromAddress()
            val splits = substring.split(".onion")
            if (splits.isEmpty()) {
                throw IllegalArgumentException("Failed to parse url")
            }

            val oAddress = OnionAddressV3(splits[0])

//            val oAddress = try {
//                OnionAddressV3(splits[0])
//            } catch (e: IllegalArgumentException) {
//                OnionAddressV4(splits[0])
//            }

            val port: Port? = splits.elementAtOrNull(1)?.let { split ->
                if (split.startsWith(':')) {
                    val portString = split.substringBefore('/').drop(1)
                    try {
                        Port(portString.toInt())
                    } catch (e: NumberFormatException) {
                        throw IllegalArgumentException("Port expressed in url ($portString) malformed", e)
                    }
                } else {
                    null
                }
            }

            val path: String = splits.elementAtOrNull(1)?.let { split ->
                if (split.contains('/')) {
                    '/' + split.substringAfter('/')
                } else {
                    ""
                }
            } ?: ""

            return OnionUrl(
                address = oAddress,
                path = path,
                port = port,
                scheme = scheme ?: Scheme.HTTP
            )
        }

        @JvmStatic
        fun fromStringOrNull(url: String): OnionUrl? {
            return try {
                fromString(url)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
