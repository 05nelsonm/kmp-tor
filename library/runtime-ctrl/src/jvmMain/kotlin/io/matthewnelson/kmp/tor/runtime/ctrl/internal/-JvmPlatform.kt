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
package io.matthewnelson.kmp.tor.runtime.ctrl.internal

import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.wrapIOException
import io.matthewnelson.kmp.process.InternalProcessApi
import io.matthewnelson.kmp.process.ReadBuffer
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.resource.OSInfo
import io.matthewnelson.kmp.tor.runtime.core.Disposable
import io.matthewnelson.kmp.tor.runtime.core.address.ProxyAddress
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.ReadableByteChannel
import java.nio.channels.SocketChannel
import java.nio.channels.WritableByteChannel
import kotlin.concurrent.Volatile

@Throws(Throwable::class)
internal actual fun ProxyAddress.connect(): CtrlConnection {
    val socket = Socket(Proxy.NO_PROXY)

    try {
        // Android may throw exception here if
        // not called from bg thread b/c of
        // InetAddress resolution
        val address = InetSocketAddress(
            address.canonicalHostname(),
            port.value,
        )

        socket.connect(address)
    } catch (t: Throwable) {
        try {
            socket.close()
        } catch (e: IOException) {
            t.addSuppressed(e)
        }

        throw t
    }

    val input = socket.getInputStream()
    val output = socket.getOutputStream()

    return Disposable {
        socket.close()
    }.toCtrlConnection(input, output)
}

@Throws(Throwable::class)
internal actual fun File.connect(): CtrlConnection = with(UnixSocketReflect) { connect() }

// Need to wrap close in Disposable b/c LocalSocket does
// not implement Closeable on Android API 16 and below.
@Suppress("BlockingMethodInNonBlockingContext")
private fun Disposable.toCtrlConnection(
    input: InputStream,
    output: OutputStream,
): CtrlConnection = object : CtrlConnection {

    @Volatile
    private var _isClosed: Boolean = false
    @Volatile
    private var _isReading: Boolean = false

    @Throws(IllegalStateException::class)
    @OptIn(InternalProcessApi::class)
    override suspend fun startRead(parser: CtrlConnection.Parser) {
        synchronized(this) {
            if (_isClosed) throw IllegalStateException("Connection is closed")
            if (_isReading) throw IllegalStateException("Already reading input")
            _isReading = true
        }

        val feed = ReadBuffer.lineOutputFeed(parser::parse)
        val buf = ReadBuffer.allocate()

        while (true) {
            val read = try {
                input.read(buf.buf)
            } catch (_: IOException) {
                break
            }

            if (read == -1) break
            feed.onData(buf, read)
        }

        buf.buf.fill(0)
        feed.close()
    }

    @Throws(IOException::class)
    override suspend fun write(command: ByteArray) {
        synchronized(this) {
            output.write(command)
            output.flush()
        }
    }

    @Throws(IOException::class)
    override fun close() {
        if (_isClosed) return

        synchronized(this) {
            if (_isClosed) return
            _isClosed = true
            this@toCtrlConnection.invoke()
        }
    }
}

private object UnixSocketReflect {

    @Throws(Throwable::class)
    fun File.connect(): CtrlConnection {
        @OptIn(InternalKmpTorApi::class)
        return if (OSInfo.INSTANCE.isAndroidRuntime()) {
            connectAndroid()
        } else {
            connectJvm()
        }
    }

    @Throws(Throwable::class)
    private fun File.connectAndroid(): CtrlConnection {
        val address = A_CONSTRUCTOR_ADDRESS.newInstance(path, A_NAMESPACE_FILESYSTEM)
        val socket = A_CONSTRUCTOR_SOCKET.newInstance()

        try {
            A_METHOD_CONNECT.invoke(socket, address)
        } catch (t: Throwable) {
            try {
                A_METHOD_CLOSE.invoke(socket)
            } catch (tt: Throwable) {
                t.addSuppressed(tt)
            }

            throw t
        }

        val input = A_METHOD_INPUT_STREAM.invoke(socket) as InputStream
        val output = A_METHOD_OUTPUT_STREAM.invoke(socket) as OutputStream

        return Disposable {
            try {
                A_METHOD_CLOSE.invoke(socket)
            } catch (t: Throwable) {
                throw t.wrapIOException()
            }
        }.toCtrlConnection(input, output)
    }

    private val A_CLAZZ_SOCKET: Class<*> by lazy {
        Class.forName("android.net.LocalSocket")
    }

    private val A_CLAZZ_ADDRESS: Class<*> by lazy {
        Class.forName("android.net.LocalSocketAddress")
    }

    private val A_CLAZZ_NAMESPACE: Class<*> by lazy {
        Class.forName("android.net.LocalSocketAddress\$Namespace")
    }

    private val A_CONSTRUCTOR_SOCKET: Constructor<*> by lazy {
        A_CLAZZ_SOCKET.getConstructor()
    }

    private val A_CONSTRUCTOR_ADDRESS: Constructor<*> by lazy {
        A_CLAZZ_ADDRESS.getConstructor(String::class.java, A_CLAZZ_NAMESPACE)
    }

    private val A_METHOD_CONNECT: Method by lazy {
        A_CLAZZ_SOCKET.getMethod("connect", A_CLAZZ_ADDRESS)
    }

    private val A_METHOD_INPUT_STREAM: Method by lazy {
        A_CLAZZ_SOCKET.getMethod("getInputStream")
    }

    private val A_METHOD_OUTPUT_STREAM: Method by lazy {
        A_CLAZZ_SOCKET.getMethod("getOutputStream")
    }

    private val A_METHOD_CLOSE: Method by lazy {
        A_CLAZZ_SOCKET.getMethod("close")
    }

    private val A_NAMESPACE_FILESYSTEM: Any by lazy {
        val constants = A_CLAZZ_NAMESPACE.enumConstants
        for (const in constants) {
            if (const.toString() != "FILESYSTEM") continue
            return@lazy const
        }

        throw ClassNotFoundException()
    }

    @Throws(Throwable::class)
    private fun File.connectJvm(): CtrlConnection {
        val address = J_METHOD_ADDRESS_OF.invoke(null, path) as SocketAddress

        val channel = NonSelectableByteChannel.open(address)
        val input = Channels.newInputStream(channel)
        val output = Channels.newOutputStream(channel)

        return Disposable {
            channel.close()
        }.toCtrlConnection(input, output)
    }

    private val J_METHOD_ADDRESS_OF: Method by lazy {
        Class.forName("java.net.UnixDomainSocketAddress")
            .getMethod("of", String::class.java)
    }

    /**
     * Wrapper that does not implement [java.nio.channels.SelectableChannel]
     *
     * https://github.com/jnr/jnr-unixsocket/blob/8424870a74ad9f0cdd8193c51a47dea955e77d92/src/main/java/jnr/unixsocket/UnixSocket.java#L292
     * */
    @JvmInline
    private value class NonSelectableByteChannel private constructor(
        private val channel: SocketChannel,
    ): ReadableByteChannel, WritableByteChannel {
        override fun close() { channel.close() }
        override fun isOpen(): Boolean = channel.isOpen
        override fun write(src: ByteBuffer?): Int = channel.write(src)
        override fun read(dst: ByteBuffer?): Int = channel.read(dst)

        companion object {

            @Throws(Throwable::class)
            fun open(address: SocketAddress): NonSelectableByteChannel {
                val channel = SocketChannel.open(address)
                return NonSelectableByteChannel(channel)
            }
        }
    }
}