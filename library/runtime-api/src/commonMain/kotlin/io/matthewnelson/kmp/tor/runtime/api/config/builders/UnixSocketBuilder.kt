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
package io.matthewnelson.kmp.tor.runtime.api.config.builders

import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.api.annotation.KmpTorDsl
import io.matthewnelson.kmp.tor.runtime.api.ThisBlock
import io.matthewnelson.kmp.tor.runtime.api.apply
import io.matthewnelson.kmp.tor.runtime.api.internal.UnixSocketsNotSupportedMessage
import io.matthewnelson.kmp.tor.runtime.api.internal.normalizedAbsolutePath
import kotlin.jvm.JvmField
import kotlin.jvm.JvmSynthetic

/**
 * For configuring a Unix Socket.
 *
 * Unix Sockets should be preferred over using a TCP Port (especially
 * for the ControlPort) as they are not affected by airplane mode. On
 * mobile devices this is especially handy.
 *
 * If airplane mode is enabled and an attempt to establish a control
 * port is made (e.g. starting tor), it will fail unless a Unix
 * Socket is being utilized.
 * */
@KmpTorDsl
public class UnixSocketBuilder private constructor() {

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
                ?.normalizedAbsolutePath()
                ?: return null

            if (path.length > 105) {
                throw UnsupportedOperationException("Unix Socket path cannot exceed 105 characters")
            }

            if (path.lines().size != 1) {
                throw UnsupportedOperationException("path cannot be multiple lines")
            }

            return "unix:\"${path}\""
        }
    }

    @InternalKmpTorApi
    public interface DSL<R: Any> {

        /**
         * Configures a Unix Socket path.
         *
         * The exception is thrown as a way to "fall back" to
         * a TCP Port configuration. This is so that you can
         * always prefer a Unix Socket when it is supported.
         *
         * @throws [UnsupportedOperationException] when:
         *   - Is Windows
         *   - Non-Android Runtime Java 15 or lower
         *   - Path length exceeds 105 characters
         * */
        @KmpTorDsl
        @Throws(UnsupportedOperationException::class)
        public fun asUnixSocket(
            block: ThisBlock<UnixSocketBuilder>,
        ): R
    }
}
