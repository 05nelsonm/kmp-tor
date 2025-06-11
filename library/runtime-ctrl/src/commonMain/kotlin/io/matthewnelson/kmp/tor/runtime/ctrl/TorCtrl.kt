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
import io.matthewnelson.kmp.tor.common.api.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.core.*
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException
import io.matthewnelson.kmp.tor.runtime.core.net.IPSocketAddress
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import kotlin.coroutines.cancellation.CancellationException

/**
 * A Tor control connection
 *
 * Issuance of [TorCmd.Signal.Halt] or [TorCmd.Signal.Shutdown] will
 * interrupt all enqueued jobs (if any) and then automatically [destroy]
 * itself when the underlying connection closes itself.
 *
 * @see [Factory]
 * */
public expect interface TorCtrl: Destroyable, TorEvent.Processor, TorCmd.Privileged.Processor {

    /**
     * Immediately disconnects from the control listener resulting
     * in interruption of all [EnqueuedJob], and invocation of all
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
     * is destroyed. If [handle] is already registered, [Disposable.noOp]
     * is returned.
     *
     * If [TorCtrl] is already destroyed, [handle] is invoked immediately
     * and [Disposable.noOp] is returned.
     *
     * [handle] should **NOT** throw exception. In the event that
     * it does, it will be delegated to [Factory.handler]. If [TorCtrl]
     * is destroyed and [handle] is invoked immediately, it will be
     * delegated to [UncaughtException.Handler.THROW]. [handle] should
     * be non-blocking, fast, and thread-safe.
     *
     * Implementations of [TorCtrl] returned by [Factory.connectAsync]
     * or [TorCtrl.Factory.connect](https://kmp-tor.matthewnelson.io/library/runtime-ctrl/io.matthewnelson.kmp.tor.runtime.ctrl/-tor-ctrl/-factory/connect.html)
     * invoke [handle] from its background thread on Jvm & Native, unless
     * immediate invocation is being had due to [TorCtrl] already being
     * destroyed, in which case it will be invoked from the context of
     * [invokeOnDestroy] caller.
     *
     * @return [Disposable] to de-register [handle] if it is no
     *   longer needed.
     * */
    public fun invokeOnDestroy(handle: ItBlock<TorCtrl>): Disposable

    public abstract class Debugger() {

        public abstract fun isEnabled(): Boolean
        public abstract operator fun invoke(log: String)

        public companion object {

            public inline fun ItBlock<String>.asDebugger(
                crossinline isEnabled: () -> Boolean
            ): Debugger
        }
    }

    /**
     * A factory class for connecting to tor via its control listener.
     *
     * See [Factory.connect](https://kmp-tor.matthewnelson.io/library/runtime-ctrl/io.matthewnelson.kmp.tor.runtime.ctrl/-tor-ctrl/-factory/connect.html
     * @see [connectAsync]
     * @param [staticTag] Special string that will exclude [TorEvent.Observer]
     *   with the same tag from removal until destroyed
     * @param [observers] Some initial observers to start with, static or not.
     * @param [interceptors] Intercepts to hook into executing jobs & modify
     *   the [TorCmd] (if needed).
     * @param [defaultExecutor] The default [OnEvent.Executor] to fall back to
     *   when calling [TorEvent.Observer.notify] if it does not have its own.
     * @param [debug] A callback for debugging info. **MUST** be thread
     *   safe. Any non-[UncaughtException] it throws will be swallowed.
     * @param [handler] The [UncaughtException.Handler] to pipe bad behavior
     *   to. It **MUST** be thread-safe for Jvm & Native implementations.
     * */
    public class Factory(
        staticTag: String? = null,
        observers: Set<TorEvent.Observer> = emptySet(),
        interceptors: Set<TorCmdInterceptor<*>> = emptySet(),
        defaultExecutor: OnEvent.Executor = OnEvent.Executor.Immediate,
        debug: Debugger?,
        handler: UncaughtException.Handler,
    ) {

        internal val staticTag: String?
        internal val debug: Debugger?
        internal val observers: Set<TorEvent.Observer>
        internal val interceptors: Set<TorCmdInterceptor<*>>
        internal val defaultExecutor: OnEvent.Executor
        internal val handler: UncaughtException.Handler

        /**
         * Connects to a tor control listener via TCP socket.
         *
         * @throws [IOException] If connection attempt fails
         * */
        @Throws(CancellationException::class, IOException::class)
        public suspend fun connectAsync(address: IPSocketAddress): TorCtrl

        /**
         * Connects to a tor control listener via UnixDomainSocket.
         *
         * @throws [IOException] If connection attempt fails
         * @throws [UnsupportedOperationException] if tor, or system this is running
         *   on does not support UnixDomainSockets
         * */
        @Throws(CancellationException::class, IOException::class, UnsupportedOperationException::class)
        public suspend fun connectAsync(path: File): TorCtrl

        /** @suppress */
        @InternalKmpTorApi
        public fun tempQueue(): TempTorCmdQueue

        @Deprecated("Use primary constructor with parameter 'debug: TorCtrl.Debugger' defined instead. See TorCtrl.Debugger.asDebugger()")
        public constructor(
            staticTag: String? = null,
            observers: Set<TorEvent.Observer> = emptySet(),
            interceptors: Set<TorCmdInterceptor<*>> = emptySet(),
            defaultExecutor: OnEvent.Executor = OnEvent.Executor.Immediate,
            debugger: ItBlock<String>? = null,
            handler: UncaughtException.Handler,
        )
    }
}
