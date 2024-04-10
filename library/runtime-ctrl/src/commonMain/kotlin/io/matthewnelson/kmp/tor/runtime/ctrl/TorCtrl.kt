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
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.core.*
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException.Handler.Companion.requireInstanceIsNotSuppressed
import io.matthewnelson.kmp.tor.runtime.core.address.ProxyAddress
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
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
public expect interface TorCtrl: Destroyable, TorEvent.Processor, TorCmd.Privileged.Processor {

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
    public override fun destroy()

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
     * or [io.matthewnelson.kmp.tor.runtime.ctrl.TorCtrl.Factory.connect]
     * invoke [handle] from its background thread on Jvm & Native, unless
     * immediate invocation is being had due to [TorCtrl] already being
     * destroyed, in which case it will be invoked from the context of
     * [invokeOnDestroy] caller.
     *
     * @return [Disposable] to de-register [handle] if it is no
     *   longer needed.
     * */
    public fun invokeOnDestroy(handle: ItBlock<TorCtrl>): Disposable

    /**
     * A factory class for connecting to tor via its control listener.
     *
     * @see [connectAsync]
     * @see [io.matthewnelson.kmp.tor.runtime.ctrl.TorCtrl.Factory.connect]
     * @param [staticTag] Special string that will exclude [TorEvent.Observer]
     *   with the same tag from removal until destroyed
     * @param [initialObservers] Some initial observers to start with, static
     *   or not.
     * @param [defaultExecutor] The default [OnEvent.Executor] to fall back to
     *   when calling [TorEvent.Observer.notify] if it does not have its own.
     * @param [debugger] A callback for debugging info. **MUST** be thread
     *   safe. Any exceptions it throws will be swallowed.
     * @param [handler] The [UncaughtException.Handler] to pipe bad behavior
     *   to. It **MUST** be thread-safe for Jvm & Native implementations.
     * @throws [IllegalArgumentException] if [handler] is an instance
     *   of [UncaughtException.SuppressedHandler] (a leaked reference)
     * */
    public class Factory
    @Throws(IllegalArgumentException::class)
    public constructor(
        staticTag: String? = null,
        initialObservers: Set<TorEvent.Observer> = emptySet(),
        defaultExecutor: OnEvent.Executor = OnEvent.Executor.Unconfined,
        debugger: ItBlock<String>? = null,
        handler: UncaughtException.Handler,
    ) {

        internal val staticTag: String?
        internal val initialObservers: Set<TorEvent.Observer>
        internal val defaultExecutor: OnEvent.Executor
        internal val handler: UncaughtException.Handler
        internal val debugger: ItBlock<String>?

        /**
         * Connects to a tor control listener via TCP port.
         *
         * @throws [IOException] If connection attempt fails
         * */
        @Throws(CancellationException::class, IOException::class)
        public suspend fun connectAsync(address: ProxyAddress): TorCtrl

        /**
         * Connects to a tor control listener via UnixDomainSocket.
         *
         * @throws [IOException] If connection attempt fails
         * @throws [UnsupportedOperationException] if tor, or system this is running
         *   on does not support UnixDomainSockets
         * */
        @Throws(CancellationException::class, IOException::class, UnsupportedOperationException::class)
        public suspend fun connectAsync(path: File): TorCtrl

        @InternalKmpTorApi
        public fun tempQueue(): TempTorCmdQueue
    }
}
