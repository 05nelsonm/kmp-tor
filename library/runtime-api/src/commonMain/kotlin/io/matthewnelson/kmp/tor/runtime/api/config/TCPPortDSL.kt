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
package io.matthewnelson.kmp.tor.runtime.api.config

import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.api.annotation.KmpTorDsl
import io.matthewnelson.kmp.tor.runtime.api.address.Port

@InternalKmpTorApi
public sealed interface TCPPortAuto<out R: Any> {
    @KmpTorDsl
    public fun auto(): R
}

@InternalKmpTorApi
public sealed interface TCPPortDisable<out R: Any> {
    @KmpTorDsl
    public fun disable(): R
}

@InternalKmpTorApi
public sealed interface TCPPortPort<out R: Any, in P: Port> {
    @KmpTorDsl
    public fun port(port: P): R
}

@InternalKmpTorApi
public sealed interface TCPPortDSL<out R: Any>:
    TCPPortAuto<R>,
    TCPPortDisable<R>,
    TCPPortPort<R, Port.Proxy>
{

    // TODO: IPAddress
    // TODO: allowPortReassignment
    //  not here, but in :runtime configuration DSL
}
