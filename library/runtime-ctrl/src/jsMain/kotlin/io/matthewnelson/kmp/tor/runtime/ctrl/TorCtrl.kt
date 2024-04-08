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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT")

package io.matthewnelson.kmp.tor.runtime.ctrl

import io.matthewnelson.kmp.file.*
import io.matthewnelson.kmp.process.InternalProcessApi
import io.matthewnelson.kmp.process.ReadBuffer
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.core.*
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException.Handler.Companion.requireInstanceIsNotSuppressed
import io.matthewnelson.kmp.tor.runtime.core.address.IPAddress
import io.matthewnelson.kmp.tor.runtime.core.address.ProxyAddress
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.ctrl.internal.*
import io.matthewnelson.kmp.tor.runtime.ctrl.internal.CtrlConnection
import io.matthewnelson.kmp.tor.runtime.ctrl.internal.RealTorCtrl
import io.matthewnelson.kmp.tor.runtime.ctrl.internal.checkUnixSockedSupport
import io.matthewnelson.kmp.tor.runtime.ctrl.internal.net_createConnection
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * A Tor control connection
 *
 * Issuance of [TorCmd.Signal.Halt] or [TorCmd.Signal.Shutdown] will
 * cancel all enqueued jobs (if any) and then automatically [destroy]
 * itself when the underlying connection closes itself.
 *
 * @see [Factory]
 * */
public actual interface TorCtrl : Destroyable, TorEvent.Processor, TorCmd.Privileged.Processor {
    /**
     * Immediately disconnects from the control listener resulting
     * in cancellation of all [QueuedJob], and invocation of all
     * handles registered via [invokeOnDestroy].
     *
     * If [TorCmd.Ownership.Take] was issued for this connection,
     * then it will also stop the tor process.
     *
     * Successive invocations do nothing.
     * */
    public actual override fun destroy()

    /**
     * Register a [handle] to be invoked when this [TorCtrl] instance
     * is destroyed. If [handle] is already registered, [Disposable.NOOP]
     * is returned.
     *
     * If [TorCtrl] is already destroyed, [handle] is invoked immediately
     * and [Disposable.NOOP] is returned.
     *
     * [handle] should **NOT** throw exception. In the event that
     * it does, it will be delegated to [Factory.handler]. If [TorCtrl]
     * is destroyed and [handle] is invoked immediately, it will be
     * delegated to [UncaughtException.Handler.THROW]. [handle] should
     * be non-blocking, fast, and thread-safe.
     *
     * Implementations of [TorCtrl] returned by [Factory.connectAsync]
     * invoke [handle] from its background thread on Jvm & Native, unless
     * immediate invocation is being had due to [TorCtrl] already being
     * destroyed, in which case it will be invoked from the context of
     * [invokeOnDestroy] caller.
     *
     * @return [Disposable] to de-register [handle] if it is no
     *   longer needed.
     * */
    public actual fun invokeOnDestroy(handle: ItBlock<TorCtrl>): Disposable

    /**
     * A factory class for connecting to tor via its control listener.
     *
     * @see [connectAsync]
     * @param [staticTag] Special string that will exclude [TorEvent.Observer]
     *   with the same tag from removal until destroyed.
     * @param [initialObservers] Some initial observers to start with, static
     *   or not.
     * @param [debugger] A callback for debugging info. **MUST** be thread
     *   safe. Any exceptions it throws will be swallowed.
     * @param [handler] The [UncaughtException.Handler] to pipe bad behavior
     *   to.
     * @throws [IllegalArgumentException] if [handler] is an instance
     *   of [UncaughtException.SuppressedHandler] (a leaked reference)
     * */
    @OptIn(InternalKmpTorApi::class)
    public actual class Factory
//    @Throws(IllegalArgumentException::class)
    public actual constructor(
        internal actual val staticTag: String?,
        internal actual val initialObservers: Set<TorEvent.Observer>,
        internal actual val debugger: ItBlock<String>?,
        internal actual val handler: UncaughtException.Handler,
    ) {

        init { handler.requireInstanceIsNotSuppressed() }

        /**
         * Connects to a tor control listener via TCP port.
         *
         * @throws [IOException] If connection attempt fails
         * */
        // @Throws(CancellationException::class, IOException::class)
        public actual suspend fun connectAsync(address: ProxyAddress): TorCtrl {
            val options = js("{}")
            options["port"] = address.port.value
            options["host"] = address.address.value
            options["family"] = when (address.address) {
                is IPAddress.V4 -> 4
                is IPAddress.V6 -> 6
            }
            return withContext(Dispatchers.Main) { connect(options) }
        }

        /**
         * Connects to a tor control listener via UnixDomainSocket.
         *
         * @throws [IOException] If connection attempt fails
         * @throws [UnsupportedOperationException] if tor, or system this is running
         *   on does not support UnixDomainSockets
         * */
        // @Throws(CancellationException::class, IOException::class, UnsupportedOperationException::class)
        public actual suspend fun connectAsync(path: File): TorCtrl {
            path.checkUnixSockedSupport()

            val options = js("{}")
            options["path"] = path.path
            return withContext(Dispatchers.Main) { connect(options) }
        }

        // @Throws(IOException::class)
        @OptIn(InternalProcessApi::class)
        private suspend fun connect(options: dynamic): TorCtrl {

            // Need to potentially catch lines if they come in
            // before RealTorCtrl starts and attaches its parser
            val bufferedLines = ArrayList<String?>(1)
            var connParser: Pair<CtrlConnection.Parser, Job>? = null

            val feed = ReadBuffer.lineOutputFeed { line ->
                val parser = connParser

                if (parser == null) {
                    bufferedLines.add(line)
                } else {
                    parser.first.parse(line)
                    if (line == null) parser.second.cancel()
                }
            }

            val rBuf = ReadBuffer.allocate()

            run {
                fun callback(nread: Int, buf: dynamic) {
                    val readBuf = try {
                        val jsBuf = Buffer.wrap(buf)
                        ReadBuffer.of(jsBuf)
                    } catch (_: Throwable) {
                        rBuf
                    }

                    feed.onData(readBuf, nread)
                }

                val onReadOptions = js("{}")
                onReadOptions["buffer"] = rBuf.buf.unwrap()
                onReadOptions["callback"] = ::callback

                options["onread"] = onReadOptions
            }

            val socket = run {
                var isConnected = false
                val socket = try {
                    net_createConnection(options) { isConnected = true }
                } catch (t: Throwable) {
                    throw t.wrapIOException { "createConnection failure" }
                }

                var threw: IOException? = null
                val errorDisposable = socket.onceError { error ->
                    threw = IOException("$error")
                }

                socket.onceClose { feed.close(); rBuf.buf.fill() }

                withContext(NonCancellable) {
                    val mark = TimeSource.Monotonic.markNow()

                    while (!isConnected && threw == null) {
                        delay(1.milliseconds)

                        if (mark.elapsedNow() < 42.milliseconds) continue

                        errorDisposable.invoke()
                        socket.destroy()
                        threw = IOException("Timed out while attempting to connect")
                        break
                    }
                }

                threw?.let { throw it }

                socket.onError { /* ignore */ }
                errorDisposable.invoke()
                socket
            }

            val connection = object : CtrlConnection {
                // @Throws(CancellationException::class, IllegalStateException::class)
                override suspend fun startRead(parser: CtrlConnection.Parser) {
                    if (socket.destroyed) throw IllegalStateException("Socket is destroyed")
                    if (connParser != null) throw IllegalStateException("Already reading input")

                    val latch = Job(currentCoroutineContext().job)
                    connParser = Pair(parser, latch)

                    // Need to process any lines that may have been
                    // buffered while awaiting startRead call
                    while (bufferedLines.isNotEmpty()) {
                        val buffered = bufferedLines.removeFirst()
                        parser.parse(buffered)
                        if (buffered == null) latch.cancel()
                    }

                    // Need to keep RealTorCtrl job active until
                    // connection closure
                    latch.join()
                }

                // @Throws(CancellationException::class, IOException::class)
                override suspend fun write(command: ByteArray) {
                    if (command.isEmpty()) return

                    val wLatch = Job()
                    var dLatch: Job? = null

                    try {
                        // Must utilize string because windows
                        val immediate = socket.write(command.decodeToString()) { wLatch.cancel() }

                        if (!immediate) {
                            dLatch = Job()
                            socket.once("drain") { dLatch.cancel() }
                        }
                    } catch (t: Throwable) {
                        wLatch.cancel()
                        dLatch?.cancel()
                        throw t.toIOException()
                    }

                    dLatch?.join()
                    wLatch.join()
                }

                // @Throws(IOException::class)
                override fun close() { socket.destroy() }
            }

            val ctrl = RealTorCtrl.of(this, Dispatchers.Main, connection)

            try {
                // A slight delay is needed before returning in order
                // to ensure that the coroutine starts before able
                // to call destroy on it.
                delay(42.milliseconds)
            } catch (t: Throwable) {
                ctrl.destroy()
                throw t
            }

            return ctrl
        }

        @InternalKmpTorApi
        public actual fun tempQueue(): TempTorCmdQueue = TempTorCmdQueue.of(handler)
    }
}
