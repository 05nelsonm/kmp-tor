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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package io.matthewnelson.kmp.tor.runtime.ctrl

import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.wrapIOException
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.core.*
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException.Handler.Companion.requireInstanceIsNotSuppressed
import io.matthewnelson.kmp.tor.runtime.core.address.ProxyAddress
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.ctrl.internal.CtrlConnection
import io.matthewnelson.kmp.tor.runtime.ctrl.internal.RealTorCtrl
import io.matthewnelson.kmp.tor.runtime.ctrl.internal.checkUnixSockedSupport
import io.matthewnelson.kmp.tor.runtime.ctrl.internal.connect
import kotlinx.coroutines.*
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmOverloads

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
     *
     * @throws [IOException] if underlying Socket.close() failed
     *   for the given platform
     * */
    // @Throws(IOException::class)
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
     * or [Factory.connect] invoke [handle] from its background thread,
     * unless immediate invocation is being had due to [TorCtrl] already
     * being destroyed, in which case it will be invoked from the context
     * of [invokeOnDestroy] caller.
     *
     * @return [Disposable] to de-register [handle] if it is no
     *   longer needed.
     * */
    public actual fun invokeOnDestroy(handle: ItBlock<TorCtrl>): Disposable

    /**
     * A factory class for connecting to tor via its control listener.
     *
     * @see [connect]
     * @see [connectAsync]
     * @param [staticTag] Special string that will exclude [TorEvent.Observer]
     *   with the same tag from removal until destroyed
     * @param [initialObservers] Some initial observers to start with, static
     *   or not.
     * @param [debugger] A callback for debugging info. **MUST** be thread
     *   safe. Any exceptions it throws will be swallowed.
     * @param [handler] The [UncaughtException.Handler] to pipe bad behavior
     *   to.
     * @throws [IllegalArgumentException] if [handler] is an instance
     *   of [UncaughtException.SuppressedHandler] (a leaked reference)
     * */
    public actual class Factory
    @JvmOverloads
    @Throws(IllegalArgumentException::class)
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
        @Throws(CancellationException::class, IOException::class)
        public actual suspend fun connectAsync(address: ProxyAddress): TorCtrl {
            return connect { context ->
                withContext(context) { address.connect() }
            }
        }

        /**
         * Connects to a tor control listener via UnixDomainSocket.
         *
         * @throws [IOException] If connection attempt fails
         * @throws [UnsupportedOperationException] if tor, or system this is running
         *   on does not support UnixDomainSockets
         * */
        @Throws(CancellationException::class, IOException::class, UnsupportedOperationException::class)
        public actual suspend fun connectAsync(path: File): TorCtrl {
            path.checkUnixSockedSupport()

            return connect { context ->
                withContext(context) { path.connect() }
            }
        }

        /**
         * Connects to a tor control listener via TCP port.
         *
         * **NOTE:** This is a blocking call and should be invoked from
         * a background thread.
         *
         * @throws [IOException] If connection attempt fails
         * */
        @Throws(IOException::class)
        public fun connect(address: ProxyAddress): TorCtrl {
            return connect { address.connect() }
        }

        /**
         * Connects to a tor control listener via UnixDomainSocket.
         *
         * **NOTE:** This is a blocking call and should be invoked from
         * a background thread.
         *
         * @throws [IOException] If connection attempt fails
         * @throws [UnsupportedOperationException] if tor, or system this is running
         *   on does not support UnixDomainSockets
         * */
        @Throws(IOException::class, UnsupportedOperationException::class)
        public fun connect(path: File): TorCtrl {
            path.checkUnixSockedSupport()

            return connect { path.connect() }
        }

        @Throws(IOException::class)
        @OptIn(ExperimentalContracts::class)
        private inline fun connect(
            connect: (context: CoroutineContext) -> CtrlConnection,
        ): TorCtrl {
            contract {
                callsInPlace(connect, InvocationKind.EXACTLY_ONCE)
            }

            @OptIn(ExperimentalCoroutinesApi::class)
            val dispatcher = Dispatchers.IO.limitedParallelism(2)

            val connection = try {
                connect(dispatcher)
            } catch (t: Throwable) {
                throw t.wrapIOException()
            }

            return RealTorCtrl.of(this, dispatcher, connection)
        }

        @InternalKmpTorApi
        public actual fun tempQueue(): TempTorCmdQueue = TempTorCmdQueue.of(handler)
    }
}