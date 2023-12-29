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
package io.matthewnelson.kmp.tor.runtime.api.key

public sealed class KeyType<T: Key.Public, V: Key.Private> private constructor() {

    public abstract fun algorithm(): String

    public sealed class Address<T: AddressKey.Public, V: AddressKey.Private>: KeyType<T, V>()
    public sealed class Auth<T: AuthKey.Public, V: AuthKey.Private>: KeyType<T, V>()

    // TODO: Factory functions

    public final override fun toString(): String = algorithm()
}
