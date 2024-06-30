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
@file:Suppress("RemoveRedundantQualifierName")

package io.matthewnelson.kmp.tor.runtime.service

import io.matthewnelson.immutable.collections.toImmutableMap
import io.matthewnelson.kmp.tor.core.api.annotation.ExperimentalKmpTorApi
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.resource.SynchronizedObject
import io.matthewnelson.kmp.tor.core.resource.synchronized
import io.matthewnelson.kmp.tor.runtime.*
import io.matthewnelson.kmp.tor.runtime.FileID.Companion.toFIDString
import io.matthewnelson.kmp.tor.runtime.core.Disposable
import io.matthewnelson.kmp.tor.runtime.core.OnEvent
import io.matthewnelson.kmp.tor.runtime.core.TorEvent
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.service.AbstractTorServiceUI.Args
import io.matthewnelson.kmp.tor.runtime.service.AbstractTorServiceUI.Factory.Companion.unsafeCastAsType
import io.matthewnelson.kmp.tor.runtime.service.internal.SynchronizedInstance
import kotlinx.coroutines.*
import kotlin.concurrent.Volatile
import kotlin.jvm.JvmField
import kotlin.jvm.JvmName
import kotlin.jvm.JvmSynthetic

/**
 * Core `commonMain` abstraction which enables implementors the ability to
 * create a fully customized notifications for the running instances of
 * [TorRuntime] as they operate within a service object.
 *
 * Alternatively, use the default implementation `kmp-tor:runtime-service-ui`
 * dependency, [io.matthewnelson.kmp.tor.runtime.service.ui.KmpTorServiceUI].
 *
 * This class' API is designed as follows:
 *  - [Factory]: To be used for all [TorRuntime.ServiceFactory] instances and
 *   injected into a service object upon service creation.
 *      - Context: `SINGLETON`
 *  - [AbstractTorServiceUI]: To be created via [Factory.newInstanceUIProtected]
 *   upon service start.
 *      - Context: `SERVICE`
 *  - [InstanceState]: To be created via [AbstractTorServiceUI.newInstanceStateProtected]
 *   for every instance of [Lifecycle.DestroyableTorRuntime] operating within
 *   the service object.
 *      - Context: `INSTANCE`
 *
 * @see [io.matthewnelson.kmp.tor.runtime.service.TorServiceUI]
 * @throws [IllegalStateException] on instantiation if [args] were not those
 *   which were passed to [Factory.newInstanceUI]. See [Args].
 * */
public abstract class AbstractTorServiceUI<
    A: AbstractTorServiceUI.Args.UI,
    C: AbstractTorServiceUI.Config,
    IS: AbstractTorServiceUI.InstanceState<C>,
    >
@Throws(IllegalStateException::class)
internal constructor(
    private val args: A,
    init: Any,
): TorServiceUIUtils() {

    init {
        args.initialize()
        check(init == INIT) { "AbstractTorServiceUI cannot be extended" }
    }

    @Volatile
    private var _immutableInstanceStates: Map<FileIDKey, IS> = emptyMap()
    private val _instanceStates = SynchronizedInstance.of(LinkedHashMap<FileIDKey, IS>(1, 1.0f))

    private val serviceJob = args.scope().coroutineContext.job

    /**
     * The default [Config] that was defined for [Factory.defaultConfig]
     * */
    @JvmField
    protected val defaultConfig: C = args.config()

    @get:JvmName("instanceStates")
    protected val instanceStates: Map<FileIDKey, IS> get() = _immutableInstanceStates

    /**
     * A [CoroutineScope] which is configured as a child to the service
     * object's [CoroutineScope].
     * */
    @JvmField
    protected val serviceChildScope: CoroutineScope = CoroutineScope(context =
        args.scope().coroutineContext
        + CoroutineName("TorServiceUIScope@${args.hashCode()}")

        // In order to not expose the service's Job externally of
        // the `runtime-service` module, this scope is created with
        // a SupervisorJob that is detached (no parent Job). Its
        // cancellation is instead called via completion handler on
        // the service's Job.
        + SupervisorJob()
    )

    public fun isDestroyed(): Boolean = !serviceJob.isActive

    protected open fun onDestroy() {}

    /**
     * Indicates that there has been an update for the
     * */
    protected abstract fun onUpdate(target: FileIDKey, type: UpdateType)

    /**
     * Core `commonMain` abstraction for passing platform specific arguments
     * in an encapsulated manner when instantiating new instances of
     * [AbstractTorServiceUI] components.
     *
     * [Args] are single use items and must be consumed only once, otherwise
     * an exception is raised when [Factory.newInstanceUI] or [newInstanceState]
     * is called, resulting a service start failure.
     *
     * @see [io.matthewnelson.kmp.tor.runtime.service.TorServiceUI.Args]
     * */
    public sealed class Args private constructor(
        private val _config: Config,
        private val _scope: CoroutineScope,
    ) {

        /**
         * For [Factory.newInstanceUIProtected]
         * */
        public abstract class UI internal constructor(
            defaultConfig: Config,
            serviceScope: CoroutineScope,
            init: Any,
        ): Args(defaultConfig, serviceScope) {

            init {
                check(init == INIT) { "AbstractTorServiceUI.Args.UI cannot be extended" }
            }
        }

        /**
         * For [newInstanceStateProtected]
         * */
        public sealed class Instance(
            instanceConfig: Config,
            instanceScope: CoroutineScope,
        ): Args(instanceConfig, instanceScope)

        @Volatile
        private var _isInitialized: Boolean = false

        @OptIn(InternalKmpTorApi::class)
        private val lock = SynchronizedObject()

        @JvmSynthetic
        @Suppress("UNCHECKED_CAST")
        internal fun <C: Config> config(): C = _config as C
        @JvmSynthetic
        internal fun scope(): CoroutineScope = _scope

        @JvmSynthetic
        @Throws(IllegalStateException::class)
        internal fun initialize() {
            @OptIn(InternalKmpTorApi::class)
            val wasAlreadyInitialized = if (_isInitialized) {
                true
            } else {
                synchronized(lock) {
                    if (_isInitialized) {
                        true
                    } else {
                        _isInitialized = true
                        false
                    }
                }
            }

            check(!wasAlreadyInitialized) {
                "$this has already been used to initialize another instance"
            }
        }

        public final override fun equals(other: Any?): Boolean {
            @OptIn(InternalKmpTorApi::class)
            return other is Args && other.lock == lock
        }

        public final override fun hashCode(): Int {
            @OptIn(InternalKmpTorApi::class)
            return lock.hashCode()
        }
    }

    /**
     * Core `commonMain` abstraction for holding UI customization input from
     * consumers of `kmp-tor-service`.
     *
     * As an example implementation, see
     * [io.matthewnelson.kmp.tor.runtime.service.ui.KmpTorServiceUI.Config]
     *
     * @param [fields] A map of the field name value pairs.
     *   (e.g. `mapOf("iconOff" to R.drawable.my_icon_off)`)
     * @throws [IllegalArgumentException] if [fields] is empty
     * */
    public abstract class Config
    @ExperimentalKmpTorApi
    @Throws(IllegalArgumentException::class)
    public constructor(fields: Map<String, Any?>) {

        private val fields = fields.toImmutableMap()

        init {
            require(this.fields.isNotEmpty()) { "fields cannot be empty" }
        }

        public final override fun equals(other: Any?): Boolean {
            if (other !is Config) return false
            if (other::class != this::class) return false
            return other.fields == fields
        }

        public final override fun hashCode(): Int {
            var result = 17
            result = result * 42 + this::class.hashCode()
            result = result * 42 + fields.hashCode()
            return result
        }

        public final override fun toString(): String = buildString {
            append("TorServiceUI.Config: [")

            fields.entries.forEach { (name, value) ->
                appendLine()
                append("    ")
                append(name)
                append(": ")
                append(value.toString())
            }

            appendLine()
            append(']')
        }
    }

    /**
     * Core `commonMain` abstraction for a [Factory] class which is
     * responsible for instantiating new instances of [AbstractTorServiceUI]
     * when requested by the service object.
     *
     * Implementations are encouraged to keep it as a subclass within,
     * and use a `private constructor` for, their [UI] implementations.
     *
     * As an example implementation, see
     * [io.matthewnelson.kmp.tor.runtime.service.ui.KmpTorServiceUI.Factory]
     *
     * @see [io.matthewnelson.kmp.tor.runtime.service.TorServiceUI.Factory]
     * */
    public abstract class Factory<
            A: Args.UI,
            C: Config,
            IS: InstanceState<C>,
            UI: AbstractTorServiceUI<A, C, IS>
        > internal constructor(

        /**
         * The default [Config] to use if one was not specified when
         * [TorRuntime.Environment] was instantiated.
         * */
        @JvmField
        public val defaultConfig: C,
        init: Any
    ) {

        /**
         * Enable/disable [RuntimeEvent.LOG.DEBUG] messages for UI components.
         *
         * **NOTE:** [TorRuntime.Environment.debug] must also be set to `true`
         * for logs to be dispatched.
         *
         * e.g. (`kmp-tor:runtime-service-ui` dependency UIState debug logs)
         *
         *     UIState[fid=0438…B37D]: [
         *         actions: [
         *             ButtonAction.NewIdentity
         *             ButtonAction.RestartTor
         *             ButtonAction.StopTor
         *         ]
         *         color: ColorState.Ready
         *         icon: IconState.DataXfer
         *         progress: Progress.None
         *         text: NewNym.RateLimited[seconds=8]
         *         title: TorState.Daemon.On{100%}
         *     ]
         *
         * @see [InstanceState.debug]
         * */
        @Volatile
        public var debug: Boolean = false

        init {
            check(init == INIT) { "AbstractTorServiceUI.Factory cannot be extended" }
        }

        /**
         * Implementors **MUST** utilize [args] to instantiate a new instance
         * of the [UI] implementation. If [args] were not consumed by the
         * returned instance of [UI], an exception will be thrown by
         * [newInstanceUI]. See [Args].
         * */
        protected abstract fun newInstanceUIProtected(args: A): UI

        @JvmSynthetic
        @Throws(IllegalStateException::class)
        internal fun newInstanceUI(args: A): UI {
            val i = newInstanceUIProtected(args)

            try {
                // Should already be initialized from instance init
                // block and thus should throw exception here.
                args.initialize()
            } catch (_: IllegalStateException) {
                // args are initialized. Ensure args
                check(i.args == args) {
                    "$args were not used to instantiate instance $i"
                }

                // Set completion handler to clean up.
                with(i.serviceJob) {
                    invokeOnCompletion {
                        i.serviceChildScope.cancel()
                    }
                    invokeOnCompletion {
                        i.onDestroy()
                    }
                }

                return i
            }

            // args did not throw exception and were just initialized in this
            // function body meaning newInstanceProtected implementation held
            // onto them and returned a different instance of UI
            throw IllegalStateException("$args were not used to create $i")
        }

        internal companion object {

            @JvmSynthetic
            @Throws(IllegalArgumentException::class)
            internal fun <C: Config> Config.unsafeCastAsType(default: C): C {
                val tClazz = this::class
                val dClazz = default::class

                require(tClazz == dClazz) {
                    "this[$tClazz] is not the same type as defaultConfig[$dClazz]"
                }

                @Suppress("UNCHECKED_CAST")
                return this as C
            }
        }
    }

    /**
     * Core `commonMain` abstraction for implementors to track changes
     * via registration of [RuntimeEvent.Observer] and [TorEvent.Observer]
     * for the instance of [Lifecycle.DestroyableTorRuntime] operating
     * within the service object.
     *
     * @throws [IllegalStateException] on instantiation if [args] were not those
     *   which were passed to [newInstanceState]. See [Args].
     * */
    public abstract class InstanceState<C: Config>
    @ExperimentalKmpTorApi
    @Throws(IllegalStateException::class)
    protected constructor(args: Args.Instance): TorServiceUIUtils(), FileID {

        init {
            args.initialize()
        }

        private val args = args as AbstractTorServiceUI<*, *, *>.InstanceArgs
        private val instanceJob: Job = args.scope().coroutineContext.job

        public abstract val events: Set<TorEvent>
        public abstract val observersRuntimeEvent: Set<RuntimeEvent.Observer<*>>
        public abstract val observersTorEvent: Set<TorEvent.Observer>

        @JvmField
        public val fileID: FileID = this.args.key
        public final override val fid: String = fileID.fid

        /**
         * The config for this instance. If no config was expressed when setting
         * up the [TorRuntime.Environment], then [Factory.defaultConfig] was
         * utilized.
         * */
        @JvmField
        public val instanceConfig: C = args.config()

        /**
         * A [CoroutineScope] which is configured as a child to the [serviceChildScope].
         * */
        @JvmField
        protected val instanceScope: CoroutineScope = args.scope()

        public fun isDestroyed(): Boolean = !instanceJob.isActive

        protected open fun onDestroy() {}

        /**
         * Notifies the [AbstractTorServiceUI] that this instance some sort
         * of state change so that it may update the UI (if needed).
         * */
        protected fun postStateChange() {
            val ui = args.ui
            val key = args.key
            if (ui._immutableInstanceStates[key] != this) return
            ui.onUpdate(key, UpdateType.Changed)
        }

        protected fun observeSignalNewNym(
            tag: String?,
            executor: OnEvent.Executor?,
            onEvent: OnEvent<String?>,
        ): Disposable? {
            if (isDestroyed()) return null
            return args.observeSignalNewNym(tag, executor, onEvent)
        }

        public fun processorAction(): Action.Processor? {
            if (isDestroyed()) return null
            return args.processorAction()
        }

        public fun processorTorCmd(): TorCmd.Unprivileged.Processor? {
            if (isDestroyed()) return null
            return args.processorTorCmd()
        }

        public fun debug(lazyMessage: () -> String) {
            if (isDestroyed()) return
            args.debugger()?.invoke(lazyMessage)
        }

        init {
            instanceJob.invokeOnCompletion {
                // Remove instance from states before calling
                // onDestroy so that any postUpdate calls will
                // be ignored.
                this.args.ui.removeInstanceState(this)
            }

            instanceJob.invokeOnCompletion { onDestroy() }
        }

        public final override fun equals(other: Any?): Boolean {
            if (other !is InstanceState<*>) return false
            if (other::class != this::class) return false
            return other.instanceJob == instanceJob
        }

        public final override fun hashCode(): Int = instanceJob.hashCode()

        public final override fun toString(): String = toFIDString(defaultClassName = "TorServiceUI.InstanceState")
    }

    /**
     * Implementors **MUST** utilize [args] to instantiate a new instance
     * of the [IS] implementation. If [args] were not consumed by the
     * returned instance of [IS], an exception will be thrown by
     * [newInstanceState]. See [Args].
     * */
    protected abstract fun newInstanceStateProtected(args: Args.Instance): IS

    @JvmSynthetic
    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    internal fun newInstanceState(
        instanceConfig: Config?,
        fid: String,
        debugger: () -> ((() -> String) -> Unit)?,
        observeSignalNewNym: (tag: String?, executor: OnEvent.Executor?, onEvent: OnEvent<String?>) -> Disposable?,
        processorAction: () -> Action.Processor?,
        processorTorCmd: () -> TorCmd.Unprivileged.Processor?,
    ): Pair<CompletableJob, IS> {
        val config = instanceConfig?.unsafeCastAsType(default = defaultConfig) ?: defaultConfig

        val (instanceJob, instanceScope) = serviceChildScope.coroutineContext.let { ctx ->
            val job = SupervisorJob(ctx.job)

            job to CoroutineScope(context =
                ctx
                + CoroutineName("TorServiceUI.InstanceStateScope@${job.hashCode()}")
                + job
            )
        }

        val args = InstanceArgs(
            config,
            instanceScope,
            fid,
            debugger,
            observeSignalNewNym,
            processorAction,
            processorTorCmd,
        )
        val i = newInstanceStateProtected(args)

        try {
            args.initialize()
        } catch (_: IllegalStateException) {
            // InstanceState.hashCode() is overridden to return instanceJob.hashCode()
            check(i.hashCode() == instanceJob.hashCode()) {
                instanceJob.cancel()

                "$args were not used to instantiate instance $i"
            }

            addInstanceState(i)
            return instanceJob to i
        }

        // args did not throw exception and were just initialized in this
        // function body meaning newInstanceStateProtected implementation held
        // onto them and returned a different instance of InstanceState
        instanceJob.cancel()
        throw IllegalStateException("$args were not used to create $i")
    }

    private fun addInstanceState(instance: IS) {
        if (isDestroyed() || instance.isDestroyed()) return

        val key = instance.fileID as FileIDKey
        val post = _instanceStates.withLock {
            if (isDestroyed() || instance.isDestroyed()) return@withLock false
            val i = get(key)
            if (i == instance) return@withLock false

            put(key, instance)
            _immutableInstanceStates = toImmutableMap()
            true
        }

        if (!isDestroyed() && !instance.isDestroyed() && post) {
            onUpdate(key, UpdateType.Added)
        }
    }

    private fun removeInstanceState(instance: InstanceState<*>) {
        val key = instance.fileID as FileIDKey
        val post = _instanceStates.withLock {
            val i = get(key)
            if (i != instance) return@withLock false

            remove(key)
            _immutableInstanceStates = toImmutableMap()
            true
        }

        if (!isDestroyed() && post) {
            onUpdate(key, UpdateType.Removed)
        }
    }

    private inner class InstanceArgs(
        instanceConfig: Config,
        instanceScope: CoroutineScope,
        fid: String,
        val debugger: () -> ((() -> String) -> Unit)?,
        val observeSignalNewNym: (tag: String?, executor: OnEvent.Executor?, onEvent: OnEvent<String?>) -> Disposable?,
        val processorAction: () -> Action.Processor?,
        val processorTorCmd: () -> TorCmd.Unprivileged.Processor?,
    ): Args.Instance(instanceConfig, instanceScope) {

        val key = FileIDKey.of(fid)
        val ui = this@AbstractTorServiceUI
    }

    public final override fun equals(other: Any?): Boolean {
        if (other !is AbstractTorServiceUI<*, *, *>) return false
        if (other::class != this::class) return false
        return other.args == args
    }

    public final override fun hashCode(): Int = args.hashCode()

    protected companion object {

        // Synthetic access to inhibit extension of abstract
        // classes externally. Only accessible within the
        // TorServiceUI class, and within the `runtime-service`
        // module.
        @JvmSynthetic
        internal val INIT = Any()
    }
}
