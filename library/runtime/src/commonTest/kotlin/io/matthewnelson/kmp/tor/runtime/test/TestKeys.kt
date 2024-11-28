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
package io.matthewnelson.kmp.tor.runtime.test

import io.matthewnelson.immutable.collections.immutableSetOf
import io.matthewnelson.immutable.collections.toImmutableSet
import io.matthewnelson.kmp.tor.runtime.core.key.X25519
import io.matthewnelson.kmp.tor.runtime.core.key.X25519.PrivateKey.Companion.toX25519PrivateKey
import io.matthewnelson.kmp.tor.runtime.core.key.X25519.PublicKey.Companion.toX25519PublicKey

private val CLIENT_AUTH_TEST_KEYS by lazy {
    val pub1 = byteArrayOf(
        25, -126, -57, 114, -127, 21, 26, -78,
        53, -84, 111, -119, -95, 97, 30, 56,
        89, 70, 0, 1, -101, 83, 62, 82,
        55, 100, 84, 57, -24, -28, -1, 109,
    )
    val prv1 = byteArrayOf(
        -88, -76, 0, -64, 103, 3, 48, -24,
        -96, -91, -20, 105, -2, -36, -60, 111,
        99, 76, -87, 85, 58, -54, 69, 86,
        -96, 107, -53, -93, 106, -11, 70, 87,
    )

    val pub2 = byteArrayOf(
        100, 59, 2, 66, -103, -67, 5, 73,
        19, 106, 69, -106, -13, 49, -86, -17,
        -113, 51, -4, 74, -65, 77, -122, -90,
        80, -125, 45, 81, -53, 61, -54, 66,
    )
    val prv2 = byteArrayOf(
        72, 127, 34, -97, -98, 84, -74, -106,
        63, -78, -47, -19, 63, -34, -13, -14,
        18, -77, 88, 127, 5, 85, -59, 100,
        116, 124, -128, -125, -102, -103, -128, 65,
    )

    val pub3 = byteArrayOf(
        67, -57, -106, 104, 23, 112, 0, -77,
        50, 91, 69, 28, 17, -107, -73, -80,
        -112, 67, -11, -118, -74, 18, 57, 108,
        -52, 83, 126, 10, 2, 70, -73, 91,
    )
    val prv3 = byteArrayOf(
        -80, 25, 97, 20, 3, -70, -84, -19,
        88, -124, 48, 100, 19, -83, 116, 94,
        -20, -108, -24, 124, -113, -4, -99, -25,
        53, 71, -68, -10, -86, 51, -55, 99,
    )

    immutableSetOf(
        pub1 to prv1,
        pub2 to prv2,
        pub3 to prv3,
    )
}

fun TestServiceFactory.testClientAuthKeyPairs(): Set<Pair<X25519.PublicKey, X25519.PrivateKey>> {
    return CLIENT_AUTH_TEST_KEYS.mapTo(LinkedHashSet(3, 1.0f)) { (pub, prv) ->
        pub.toX25519PublicKey() to prv.toX25519PrivateKey()
    }.toImmutableSet()
}
