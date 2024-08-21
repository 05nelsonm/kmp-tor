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
package io.matthewnelson.kmp.tor.runtime.core.config.builder

import io.matthewnelson.kmp.file.*
import io.matthewnelson.kmp.file.normalize
import io.matthewnelson.kmp.tor.core.api.annotation.ExperimentalKmpTorApi
import io.matthewnelson.kmp.tor.core.api.annotation.KmpTorDsl
import io.matthewnelson.kmp.tor.runtime.core.ThisBlock
import io.matthewnelson.kmp.tor.runtime.core.address.Port
import io.matthewnelson.kmp.tor.runtime.core.apply
import io.matthewnelson.kmp.tor.runtime.core.config.TorOption
import io.matthewnelson.kmp.tor.runtime.core.config.TorSetting
import io.matthewnelson.kmp.tor.runtime.core.config.TorSetting.LineItem.Companion.toLineItem
import io.matthewnelson.kmp.tor.runtime.core.internal.toUnixSocketPath
import kotlin.jvm.JvmField
import kotlin.jvm.JvmSynthetic

/**
 * A DSL builder scope for configuring [TorOption.HiddenServicePort].
 *
 * @see [BuilderScopeHS.port]
 * @see [TorOption.HiddenServiceDir.asSetting]
 * */
@KmpTorDsl
public class BuilderScopeHSPort private constructor(

    /**
     * TODO
     * */
    @JvmField
    public val virtual: Port,
) {

    private var _target: String = virtual.toString()

    // TODO: IPAddress. Issue #313

    /**
     * This sets the "target" argument to a TCP [Port] for which
     * incoming http requests will be directed locally on the host.
     *
     * e.g.
     *
     *     target(port = 8080.toPort())
     * */
    @KmpTorDsl
    public fun target(
        port: Port,
    ): BuilderScopeHSPort {
        _target = port.toString()
        return this
    }

    /**
     * This sets the "target" argument to a Unix Socket path for which
     * incoming http requests will be directed locally on the host. The
     * [unixSocket] result will be formatted as `unix:\"${file-path}\"`
     * after being sanitized via [File.absoluteFile] + [File.normalize].
     *
     * e.g.
     *
     *     try {
     *         target(unixSocket = "/path/to/server/dir/uds.sock".toFile())
     *     } catch(_: UnsupportedOperationException) {
     *         target(port = 8080.toPort())
     *     }
     *
     * @throws [UnsupportedOperationException] when:
     *   - Is Windows (tor does not support Unix Sockets on windows).
     *   - Is Java 15 or below (Jvm only, Android is always available).
     *   - Configured path exceeds `104` characters in length.
     *   - Configured path is multiple lines.
     * */
    @KmpTorDsl
    @Throws(UnsupportedOperationException::class)
    public fun target(
        unixSocket: File,
    ): BuilderScopeHSPort {
        val path = unixSocket.toUnixSocketPath()
        _target = path
        return this
    }

    internal companion object {

        @JvmSynthetic
        internal fun <B: DSL<B>> B.configureHSPort(
            virtual: Port,
            ports: LinkedHashSet<TorSetting.LineItem>,
            block: ThisBlock<BuilderScopeHSPort>,
        ): B {
            val b = BuilderScopeHSPort(virtual).apply(block)
            // TODO: IPAddress. Issue #313
            val argument = "$virtual ${b._target}"

            @OptIn(ExperimentalKmpTorApi::class)
            val item = TorOption.HiddenServicePort.toLineItem(argument)
            ports.add(item)
            return this
        }
    }

    internal interface DSL<B: DSL<B>> {

        /**
         * TODO
         * */
        @KmpTorDsl
        public fun port(
            virtual: Port,
        ): B

        /**
         * TODO
         * */
        @KmpTorDsl
        public fun port(
            virtual: Port,
            block: ThisBlock<BuilderScopeHSPort>,
        ): B
    }
}