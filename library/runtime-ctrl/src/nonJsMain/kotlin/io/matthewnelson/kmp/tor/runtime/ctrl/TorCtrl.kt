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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "KotlinRedundantDiagnosticSuppress")

package io.matthewnelson.kmp.tor.runtime.ctrl

import io.matthewnelson.immutable.collections.toImmutableSet
import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.InterruptedException
import io.matthewnelson.kmp.file.wrapIOException
import io.matthewnelson.kmp.process.Blocking
import io.matthewnelson.kmp.tor.common.api.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.core.*
import io.matthewnelson.kmp.tor.runtime.core.net.IPSocketAddress
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.ctrl.TorCtrl.Debugger.Companion.asDebugger
import io.matthewnelson.kmp.tor.runtime.ctrl.internal.*
import kotlinx.coroutines.*
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import kotlin.time.Duration.Companion.milliseconds

/**
 * A Tor control connection
 *
 * Issuance of [TorCmd.Signal.Halt] or [TorCmd.Signal.Shutdown] will
 * interrupt all enqueued jobs (if any) and then automatically [destroy]
 * itself when the underlying connection closes itself.
 *
 * @see [Factory]
 * */
public actual interface TorCtrl : Destroyable, TorEvent.Processor, TorCmd.Privileged.Processor {

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
    public actual override fun destroy()

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
     * or [Factory.connect] invoke [handle] from its background thread,
     * unless immediate invocation is being had due to [TorCtrl] already
     * being destroyed, in which case it will be invoked from the context
     * of [invokeOnDestroy] caller.
     *
     * @return [Disposable] to de-register [handle] if it is no
     *   longer needed.
     * */
    public actual fun invokeOnDestroy(handle: ItBlock<TorCtrl>): Disposable

    public actual abstract class Debugger {

        public actual abstract fun isEnabled(): Boolean
        public actual abstract operator fun invoke(log: String)

        public actual companion object {

            @JvmStatic
            @JvmName("from")
            public actual inline fun ItBlock<String>.asDebugger(
                crossinline isEnabled: () -> Boolean,
            ): Debugger = object : Debugger() {
                override fun isEnabled(): Boolean = isEnabled.invoke()
                override fun invoke(log: String) = this@asDebugger(log)
            }
        }
    }

    /**
     * A factory class for connecting to tor via its control listener.
     *
     * @see [connect]
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
     *   to. It **MUST** be thread-safe.
     * */
    public actual class Factory
    @JvmOverloads
    public actual constructor(
        internal actual val staticTag: String?,
        observers: Set<TorEvent.Observer>,
        interceptors: Set<TorCmdInterceptor<*>>,
        internal actual val defaultExecutor: OnEvent.Executor,
        internal actual val debug: Debugger?,
        internal actual val handler: UncaughtException.Handler,
    ) {

        internal actual val observers: Set<TorEvent.Observer> = observers.toImmutableSet()
        internal actual val interceptors: Set<TorCmdInterceptor<*>> = interceptors.toImmutableSet()

        /**
         * Connects to a tor control listener via TCP socket.
         *
         * @throws [IOException] If connection attempt fails
         * */
        @Throws(CancellationException::class, IOException::class)
        public actual suspend fun connectAsync(address: IPSocketAddress): TorCtrl {
            return withDelayedReturnAsync {
                connect { context ->
                    withContext(context) {
                        address.connect()
                    }
                }
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
            path.checkUnixSocketSupport()

            return withDelayedReturnAsync {
                connect { context ->
                    withContext(context) {
                        path.connect()
                    }
                }
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
        public fun connect(address: IPSocketAddress): TorCtrl {
            return withDelayedReturn {
                connect { address.connect() }
            }
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
            path.checkUnixSocketSupport()

            return withDelayedReturn {
                connect { path.connect() }
            }
        }

        @JvmOverloads
        @Deprecated("Use primary constructor with parameter 'debug: TorCtrl.Debugger' defined instead. See TorCtrl.Debugger.asDebugger()")
        public actual constructor(
            staticTag: String?,
            observers: Set<TorEvent.Observer>,
            interceptors: Set<TorCmdInterceptor<*>>,
            defaultExecutor: OnEvent.Executor,
            debugger: ItBlock<String>?,
            handler: UncaughtException.Handler,
        ): this(
            staticTag,
            observers,
            interceptors,
            defaultExecutor,
            debugger?.asDebugger { true },
            handler,
        )

        @Suppress("NOTHING_TO_INLINE", "LocalVariableName")
        @Throws(CancellationException::class, IOException::class)
        @OptIn(ExperimentalContracts::class, ExperimentalCoroutinesApi::class)
        private inline fun connect(
            connect: (context: CoroutineContext) -> CtrlConnection,
        ): RealTorCtrl {
            contract {
                callsInPlace(connect, InvocationKind.EXACTLY_ONCE)
            }

            val dispatcher = newTorCtrlDispatcher()

            val connection = try {
                connect(dispatcher)
            } catch (t: Throwable) {
                dispatcher.close()
                if (t is CancellationException) throw t
                throw t.wrapIOException()
            }

            val close = Executable.Once.of(concurrent = true) { dispatcher.close() }

            return RealTorCtrl.of(this, dispatcher, connection, closeDispatcher = { LOG ->
                try {
                    @OptIn(DelicateCoroutinesApi::class)
                    GlobalScope.launch(Dispatchers.IO) {
                        try {
                            delay(250.milliseconds)
                        } finally {
                            close.execute()
                            LOG.d { "Dispatchers Closed" }
                        }
                    }
                } catch (_: Throwable) {
                    close.execute()
                }
            })
        }

        /**
         * A slight delay is needed before returning in order
         * to ensure that the coroutine starts before able
         * to call destroy on it.
         * */
        @Suppress("NOTHING_TO_INLINE")
        @OptIn(ExperimentalContracts::class)
        private inline fun withDelayedReturn(block: () -> RealTorCtrl): TorCtrl {
            contract {
                callsInPlace(block, InvocationKind.EXACTLY_ONCE)
            }

            val ctrl = block()

            while (!ctrl.isReady) {
                try {
                    Blocking.threadSleep(5.milliseconds)
                } catch (_: InterruptedException) {}
            }

            try {
                Blocking.threadSleep(5.milliseconds)
            } catch (_: InterruptedException) {}

            return ctrl
        }

        /**
         * A slight delay is needed before returning in order
         * to ensure that the coroutine starts before able
         * to call destroy on it.
         * */
        @Suppress("NOTHING_TO_INLINE")
        @OptIn(ExperimentalContracts::class)
        private suspend inline fun withDelayedReturnAsync(block: () -> RealTorCtrl): TorCtrl {
            contract {
                callsInPlace(block, InvocationKind.EXACTLY_ONCE)
            }

            val ctrl = block()

            withContext(NonCancellable) {
                while (!ctrl.isReady) {
                    delay(5.milliseconds)
                }

                delay(5.milliseconds)
            }

            return ctrl
        }

        /** @suppress */
        @InternalKmpTorApi
        public actual fun tempQueue(): TempTorCmdQueue = TempTorCmdQueue.of(handler)
    }
}
