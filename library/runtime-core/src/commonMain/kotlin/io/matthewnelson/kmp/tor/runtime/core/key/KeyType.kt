/*
 * Copyright (c) 2023 Matthew Nelson
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
package io.matthewnelson.kmp.tor.runtime.core.key

import io.matthewnelson.kmp.tor.runtime.core.address.OnionAddress

/**
 * Type definition for public/private key associations utilized by tor.
 * */
public sealed class KeyType<T: Key.Public, V: Key.Private> private constructor() {

    /**
     * The algorithm name for this [KeyType].
     * */
    public abstract fun algorithm(): String

    /**
     * Represents a [KeyType] associated with [OnionAddress], such as
     * adding a Hidden Service to a running tor client via its control
     * connection.
     *
     * @see [ED25519_V3]
     * */
    public sealed class Address<T: AddressKey.Public, V: AddressKey.Private>: KeyType<T, V>()

    /**
     * Represents a [KeyType] associated with client authentication,
     * such as adding a private key to your running client in order
     * to connect to a Hidden Service which is using client authentication.
     *
     * @see [X25519]
     * */
    public sealed class Auth<T: AuthKey.Public, V: AuthKey.Private>: KeyType<T, V>()

    /** @suppress */
    public final override fun toString(): String = algorithm()
}
