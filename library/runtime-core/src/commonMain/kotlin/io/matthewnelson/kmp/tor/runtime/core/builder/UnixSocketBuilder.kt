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
package io.matthewnelson.kmp.tor.runtime.core.builder

import io.matthewnelson.kmp.file.*
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.api.annotation.KmpTorDsl
import io.matthewnelson.kmp.tor.runtime.core.ThisBlock
import io.matthewnelson.kmp.tor.runtime.core.TorConfig
import io.matthewnelson.kmp.tor.runtime.core.apply
import io.matthewnelson.kmp.tor.runtime.core.internal.UnixSocketsNotSupportedMessage
import kotlin.jvm.JvmField
import kotlin.jvm.JvmSynthetic

/**
 * For configuring a Unix Socket path.
 *
 * Unix Sockets should be preferred over using a TCP Port (especially
 * for the ControlPort) as they are not affected by airplane mode. On
 * mobile devices this is especially handy.
 *
 * If airplane mode is enabled and an attempt to establish a control
 * port is made (e.g. starting tor), it will fail unless a Unix
 * Socket is being utilized.
 *
 * They are also superior in terms of privacy as they are not touching
 * the TCP layer which almost all applications have access to on the
 * device.
 * */
@KmpTorDsl
public class UnixSocketBuilder private constructor() {

    /**
     * The file path for the Unix Socket.
     *
     * e.g.
     *
     *     "/path/to/my/hs.sock".toFile()
     *
     * The result will be prefixed with `unix:` and quoted for the
     * [TorConfig.LineItem] format that tor expects.
     * */
    @JvmField
    public var file: File? = null

    public companion object {

        /**
         * A default file name to use (if desired) when configuring
         * a ControlPort to use a unix socket.
         * */
        public const val DEFAULT_NAME_CTRL: String = "ctrl.sock"

        /**
         * A default file name to use (if desired) when configuring
         * a HiddenServicePort to use a unix socket.
         * */
        public const val DEFAULT_NAME_HS: String = "hs.sock"

        /**
         * A default file name to use (if desired) when configuring
         * a SocksPort to use a unix socket.
         * */
        public const val DEFAULT_NAME_SOCKS: String = "socks.sock"

        @JvmSynthetic
        @Throws(UnsupportedOperationException::class)
        internal fun build(
            block: ThisBlock<UnixSocketBuilder>,
        ): String? {
            UnixSocketsNotSupportedMessage?.let { throw UnsupportedOperationException(it) }

            val path = UnixSocketBuilder()
                .apply(block)
                .file
                ?.absoluteFile
                ?.normalize()
                ?.path
                ?: return null

            if (path.length > 104) {
                throw UnsupportedOperationException("path cannot exceed 104 characters")
            }

            if (path.lines().size != 1) {
                throw UnsupportedOperationException("path cannot be multiple lines")
            }

            return "unix:\"${path}\""
        }
    }

    /**
     * Not meant for public use.
     * */
    @InternalKmpTorApi
    public interface DSL<R: Any> {

        /**
         * For a [TorConfig.Keyword] that can be configured to use
         * either a TCP port, or a Unix Socket. This will configure
         * it to use a Unix Socket.
         *
         * [UnsupportedOperationException] is thrown when Unix Sockets
         * are unavailable for the given platform or runtime as a way
         * to "fall back" to a TCP Port configuration for the given
         * [TorConfig.Setting]. This is so that you can always prefer a
         * Unix Socket when it is supported.
         *
         * e.g.
         *
         *     try {
         *         asUnixSocket {
         *             file = "/path/to/my/hs.sock".toFile()
         *         }
         *     } catch(_: UnsupportedOperationException) {
         *         asPort {
         *             auto()
         *         }
         *     }
         *
         * @throws [UnsupportedOperationException] when:
         *   - Is Windows
         *   - Java 15 or below (Jvm only, Android is OK for all APIs)
         *   - Configured path exceeds 104 characters in length
         *   - Configured path is multiple lines
         * */
        @KmpTorDsl
        @Throws(UnsupportedOperationException::class)
        public fun asUnixSocket(
            block: ThisBlock<UnixSocketBuilder>,
        ): R
    }
}
