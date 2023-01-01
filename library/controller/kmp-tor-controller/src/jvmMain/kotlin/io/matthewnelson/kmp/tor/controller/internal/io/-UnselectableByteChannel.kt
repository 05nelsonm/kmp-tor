/*
 * Copyright (c) 2023 Matthew Nelson
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
package io.matthewnelson.kmp.tor.controller.internal.io

import java.nio.ByteBuffer
import java.nio.channels.ReadableByteChannel
import java.nio.channels.SocketChannel
import java.nio.channels.WritableByteChannel

/**
 * Wrapper that does not implement [java.nio.channels.SelectableChannel]
 *
 * https://github.com/jnr/jnr-unixsocket/blob/8424870a74ad9f0cdd8193c51a47dea955e77d92/src/main/java/jnr/unixsocket/UnixSocket.java#L292
 * */
@JvmInline
@Suppress("SpellCheckingInspection")
internal value class UnselectableByteChannel(
    private val channel: SocketChannel
): ReadableByteChannel, WritableByteChannel {

    init {
        require(channel.isConnected) { "Channel must be connected" }
    }

    override fun close() { channel.close() }
    override fun isOpen(): Boolean = channel.isOpen
    override fun write(src: ByteBuffer?): Int = channel.write(src)
    override fun read(dst: ByteBuffer?): Int = channel.read(dst)
}
