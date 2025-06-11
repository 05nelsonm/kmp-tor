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
@file:Suppress("OPTIONAL_DECLARATION_USAGE_IN_NON_COMMON_SOURCE")

package io.matthewnelson.kmp.tor.runtime.service

import io.matthewnelson.immutable.collections.toImmutableMap
import io.matthewnelson.kmp.tor.common.api.ExperimentalKmpTorApi
import io.matthewnelson.kmp.tor.common.api.InternalKmpTorApi
import io.matthewnelson.kmp.tor.common.core.synchronized
import io.matthewnelson.kmp.tor.common.core.synchronizedObject
import io.matthewnelson.kmp.tor.runtime.*
import io.matthewnelson.kmp.tor.runtime.FileID.Companion.toFIDString
import io.matthewnelson.kmp.tor.runtime.core.Disposable
import io.matthewnelson.kmp.tor.runtime.core.Executable
import io.matthewnelson.kmp.tor.runtime.core.OnEvent
import io.matthewnelson.kmp.tor.runtime.core.TorEvent
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.service.AbstractTorServiceUI.Factory.Companion.unsafeCastAsType
import kotlinx.coroutines.*
import kotlin.concurrent.Volatile
import kotlin.jvm.JvmField
import kotlin.jvm.JvmName
import kotlin.jvm.JvmSynthetic

/**
 * Core `commonMain` abstraction which enables implementors the ability to
 * create a fully customized notification for the running instances of
 * [TorRuntime] as they operate within a service object.
 *
 * This class' API is designed as follows:
 *  - [Factory]: To be used for all [TorRuntime.ServiceFactory] instances and
 *   injected into a service object upon service creation.
 *      - Context: `SINGLETON`
 *  - [AbstractTorServiceUI]: To be created via [Factory.createProtected]
 *   upon service start.
 *      - Context: `SERVICE`
 *  - [InstanceState]: To be created via [AbstractTorServiceUI.createProtected]
 *   for every instance of [Lifecycle.DestroyableTorRuntime] operating within
 *   the service object.
 *      - Context: `INSTANCE`
 *
 * See [TorServiceUI]
 * See [ui.KmpTorServiceUI]
 * @throws [IllegalStateException] on instantiation if [args] were not those
 *   which were passed to [Factory.createProtected]. See [Args].
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
) {

    init {
        args.initialize()
        check(init == INIT) { "AbstractTorServiceUI cannot be extended" }
    }

    @Volatile
    private var _selectorState: SelectorState = SelectorState(previous = null, displayed = null, next = null)
    @Volatile
    private var _instanceStates: Map<FileIDKey, IS> = emptyMap()

    @OptIn(InternalKmpTorApi::class)
    private val stateLock = synchronizedObject()

    private val serviceJob = args.scope().coroutineContext.job

    /**
     * The default [Config] that was defined for [Factory.defaultConfig]
     * */
    @JvmField
    protected val defaultConfig: C = args.config()

    /**
     * Returns the currently displayed [InstanceState].
     *
     * @see [selectPrevious]
     * @see [selectNext]
     * */
    @get:JvmName("displayed")
    protected val displayed: IS? get() = _selectorState.displayed?.let { _instanceStates[it] }

    /**
     * All [InstanceState] currently operating within this UI "container".
     * */
    @get:JvmName("instanceStates")
    protected val instanceStates: Collection<IS> get() = _instanceStates.values

    /**
     * A [CoroutineScope] which is configured as a child to the service
     * object's [CoroutineScope] which utilizes [Dispatchers.Main]
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
     * Indicates there was a stateful change that requires an update to the UI.
     *
     * **NOTE:** This is always invoked from the UI thread
     *
     * @param [displayed] The current [InstanceState] selected to be shown
     * @param [hasPrevious] Indicates there is another [InstanceState]
     *   that exists in [instanceStates] before [displayed].
     * @param [hasNext] Indicates there is another [InstanceState]
     *   that exists in [instanceStates] after [displayed].
     * @see [selectPrevious]
     * @see [selectNext]
     * */
    protected abstract fun onRender(displayed: IS, hasPrevious: Boolean, hasNext: Boolean)

    /**
     * Shifts the pointer to the "left" and calls [onRender] with the new
     * parameters. If no previous [InstanceState] is available, then nothing
     * occurs.
     *
     * This is for cycling through running instances via some button in the
     * UI, such as `<`.
     * */
    protected fun selectPrevious() {
        if (!_selectorState.hasPrevious) return

        @OptIn(InternalKmpTorApi::class)
        synchronized(stateLock) {
            val newDisplayed = _selectorState.previous ?: return@synchronized

            // shift [ p, D, n ] -> [ ?, P, d ]
            var newPrevious: FileIDKey? = null
            for (key in _instanceStates.keys) {
                if (key == newDisplayed) break
                newPrevious = key
            }

            _selectorState = SelectorState(
                previous = newPrevious,
                displayed = newDisplayed,
                next = _selectorState.displayed
            )

            _instanceStates[newDisplayed]?.postUpdate()
        }
    }

    /**
     * Shifts the pointer to the "right" and calls [onRender] with the new
     * parameters. If no next [InstanceState] is available, then nothing
     * occurs.
     *
     * This is for cycling through running instances via some button in the
     * UI, such as `>`.
     * */
    protected fun selectNext() {
        if (!_selectorState.hasNext) return

        @OptIn(InternalKmpTorApi::class)
        synchronized(stateLock) {
            val newDisplayed = _selectorState.next ?: return@synchronized

            // shift [ p, D, n ] -> [ d, N, ? ]
            var takeNextKey = false
            val newNext = _instanceStates.keys.firstOrNull { key ->
                if (takeNextKey) return@firstOrNull true

                if (key == newDisplayed) {
                    takeNextKey = true
                }
                false
            }

            _selectorState = SelectorState(
                previous = _selectorState.displayed,
                displayed = newDisplayed,
                next = newNext
            )

            _instanceStates[newDisplayed]?.postUpdate()
        }
    }

    /**
     * Core `commonMain` abstraction for passing platform specific arguments,
     * in an encapsulated manner, when instantiating new instances of
     * [AbstractTorServiceUI] components.
     *
     * [Args] are single use items and must be consumed only once, otherwise
     * an exception is raised when [Factory.createProtected] or [createProtected]
     * is called resulting a service start failure.
     *
     * See [TorServiceUI.Args](https://kmp-tor.matthewnelson.io/library/runtime-service/io.matthewnelson.kmp.tor.runtime.service/-tor-service-u-i/-args/index.html)
     * */
    public sealed class Args private constructor(
        private val _config: Config,
        private val _scope: CoroutineScope,
    ) {

        /**
         * For [Factory.createProtected]
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
         * For [AbstractTorServiceUI.createProtected]
         * */
        public sealed class Instance(
            instanceConfig: Config,
            instanceScope: CoroutineScope,
        ): Args(instanceConfig, instanceScope)

        @Volatile
        private var _isInitialized: Boolean = false

        @OptIn(InternalKmpTorApi::class)
        private val lock = synchronizedObject()

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

        /** @suppress */
        public final override fun equals(other: Any?): Boolean {
            @OptIn(InternalKmpTorApi::class)
            return other is Args && other.lock == lock
        }

        /** @suppress */
        public final override fun hashCode(): Int {
            @OptIn(InternalKmpTorApi::class)
            return lock.hashCode()
        }
    }

    /**
     * Core `commonMain` abstraction for holding UI customization input from
     * consumers of `kmp-tor-service`.
     *
     * **NOTE:** This is currently an [ExperimentalKmpTorApi] when extending
     * to create your own implementation. Things may change (as the annotation
     * states), so use at your own risk! Prefer using the stable implementation
     * via the `kmp-tor:runtime-service-ui` dependency.
     *
     * See [KmpTorServiceUI.Config](https://kmp-tor.matthewnelson.io/library/runtime-service-ui/io.matthewnelson.kmp.tor.runtime.service.ui/-kmp-tor-service-u-i/-config/index.html)
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

        /** @suppress */
        public final override fun equals(other: Any?): Boolean {
            if (other !is Config) return false
            if (other::class != this::class) return false
            return other.fields == fields
        }

        /** @suppress */
        public final override fun hashCode(): Int {
            var result = 17
            result = result * 42 + this::class.hashCode()
            result = result * 42 + fields.hashCode()
            return result
        }

        /** @suppress */
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
     * See [TorServiceUI.Factory](https://kmp-tor.matthewnelson.io/library/runtime-service/io.matthewnelson.kmp.tor.runtime.service/-tor-service-u-i/-factory/index.html)
     * See [KmpTorServiceUI.Factory](https://kmp-tor.matthewnelson.io/library/runtime-service-ui/io.matthewnelson.kmp.tor.runtime.service.ui/-kmp-tor-service-u-i/-factory/index.html)
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
         * e.g. (`kmp-tor:runtime-service-ui` dependency `UIState` debug logs)
         *
         *     UIState[fid=0438…B37D]: [
         *         actions: [
         *             ButtonAction.NewIdentity
         *             ButtonAction.RestartTor
         *             ButtonAction.StopTor
         *         ]
         *         icon: IconState.Data[colorize=true]
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
         * of the [AbstractTorServiceUI] implementation. If [args] were not
         * consumed by the returned instance, an exception will be thrown by
         * [createProtected].
         *
         * @see [Args].
         * */
        protected abstract fun createProtected(args: A): UI

        @JvmSynthetic
        @Throws(IllegalStateException::class)
        internal fun create(args: A): UI {
            val i = createProtected(args)

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
     * Core `commonMain` abstraction for implementors to track changes via
     * registration of [RuntimeEvent.Observer] and [TorEvent.Observer] for
     * the instance of [Lifecycle.DestroyableTorRuntime] operating within
     * the service object.
     *
     * The primary objective of the [InstanceState] API is to observe the
     * [Lifecycle.DestroyableTorRuntime] instance operating within the service
     * object and react by updating some sort of stateful object (whatever
     * that may be for the implementation), then notify the UI "container"
     * via [postStateChange] that a change has occurred.
     *
     * **NOTE:** This is currently an [ExperimentalKmpTorApi] when extending
     * to create your own implementation. Things may change (as the annotation
     * states), so use at your own risk! Prefer using the stable implementation
     * via the `kmp-tor:runtime-service-ui` dependency.
     *
     * @throws [IllegalStateException] on instantiation if [args] were not those
     *   which were passed to [createProtected]. See [Args].
     * */
    public abstract class InstanceState<C: Config>
    @ExperimentalKmpTorApi
    @Throws(IllegalStateException::class)
    protected constructor(args: Args.Instance): FileID {

        init {
            args.initialize()
        }

        private val args = args as AbstractTorServiceUI<*, *, *>.InstanceArgs
        private val instanceJob: Job = args.scope().coroutineContext.job

        /**
         * The required [TorEvent]s needed for this implementation to function.
         *
         * Used for the [TorRuntime.ServiceFactory.Binder.onBind] argument.
         * */
        public abstract val events: Set<TorEvent>

        /**
         * The [RuntimeEvent.Observer]s that will be added upon instantiation
         * of [Lifecycle.DestroyableTorRuntime].
         *
         * Used for the [TorRuntime.ServiceFactory.Binder.onBind] argument.
         * */
        public abstract val observersRuntimeEvent: Set<RuntimeEvent.Observer<*>>

        /**
         * The [TorEvent.Observer]s that will be added upon instantiation
         * of [Lifecycle.DestroyableTorRuntime].
         *
         * Used for the [TorRuntime.ServiceFactory.Binder.onBind] argument.
         * */
        public abstract val observersTorEvent: Set<TorEvent.Observer>

        public final override val fid: String = this.args.key.fid

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
         * Notifies the [AbstractTorServiceUI] that this instance had some
         * sort of stateful change so that it may update the UI (if needed).
         *
         * If this [InstanceState] is not the currently [displayed] instance,
         * then the update is ignored and [onRender] will not be invoked.
         * */
        protected fun postStateChange() { args.ui.postStateChange(this) }

        /**
         * Exported functionality of [RuntimeEvent.EXECUTE.CMD.observeSignalNewNym]
         * with the running instance of [Lifecycle.DestroyableTorRuntime].
         *
         * **NOTE:** It may not immediately available upon instantiation of
         * [InstanceState] as the [TorRuntime.ServiceFactory.Binder] needs
         * to use arguments provided by [InstanceState] to bind.
         * */
        protected fun observeSignalNewNym(
            tag: String?,
            executor: OnEvent.Executor?,
            onEvent: OnEvent<String?>,
        ): Disposable.Once? {
            if (isDestroyed()) return null
            return args.observeSignalNewNym(tag, executor, onEvent)
        }

        /**
         * Retrieves an [Action.Processor] which will pipe actions to the
         * running instance of [Lifecycle.DestroyableTorRuntime].
         *
         * The [Action.Processor] reference **should not** be held onto.
         *
         * **NOTE:** It may not immediately available upon instantiation of
         * [InstanceState] as the [TorRuntime.ServiceFactory.Binder] needs
         * to use arguments provided by [InstanceState] to bind.
         * */
        public fun processorAction(): Action.Processor? {
            if (isDestroyed()) return null
            return args.processorAction()
        }

        /**
         * Retrieves a [TorCmd.Unprivileged.Processor] which will pipe
         * commands to the running instance of [Lifecycle.DestroyableTorRuntime].
         *
         * The [TorCmd.Unprivileged.Processor] reference **should not**
         * be held onto.
         *
         * **NOTE:** It may not immediately available upon instantiation of
         * [InstanceState] as the [TorRuntime.ServiceFactory.Binder] needs
         * to use arguments provided by [InstanceState] to bind.
         * */
        public fun processorTorCmd(): TorCmd.Unprivileged.Processor? {
            if (isDestroyed()) return null
            return args.processorTorCmd()
        }

        /**
         * Notify [RuntimeEvent.LOG.DEBUG] observers for the running instance
         * of [Lifecycle.DestroyableTorRuntime].
         *
         * @see [Factory.debug]
         * */
        public fun debug(lazyMessage: () -> String) {
            if (isDestroyed()) return
            args.debugger()?.invoke(lazyMessage)
        }

        init {
            instanceJob.invokeOnCompletion {
                // Remove instance from states before calling
                // onDestroy so that any postStateChange calls will
                // be ignored.
                this.args.ui.removeInstanceState(this)
            }

            instanceJob.invokeOnCompletion { onDestroy() }
        }

        @JvmSynthetic
        internal fun fileIDKey(): FileID = args.key

        /** @suppress */
        public final override fun equals(other: Any?): Boolean {
            if (other !is InstanceState<*>) return false
            if (other::class != this::class) return false
            return other.instanceJob == instanceJob
        }

        /** @suppress */
        public final override fun hashCode(): Int = instanceJob.hashCode()

        /** @suppress */
        public final override fun toString(): String = toFIDString(defaultClassName = "AbstractTorServiceUI.InstanceState")
    }

    /**
     * Implementors **MUST** utilize [args] to instantiate a new instance
     * of the [InstanceState] implementation. If [args] were not consumed
     * by the returned instance of [InstanceState], an exception will be
     * thrown by [createProtected].
     *
     * @see [Args].
     * */
    protected abstract fun createProtected(args: Args.Instance): IS

    @JvmSynthetic
    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    internal fun create(
        instanceConfig: Config?,
        fid: String,
        debugger: () -> ((() -> String) -> Unit)?,
        observeSignalNewNym: (tag: String?, executor: OnEvent.Executor?, onEvent: OnEvent<String?>) -> Disposable.Once?,
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
        val i = createProtected(args)

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

        val key = instance.fileIDKey() as FileIDKey

        @OptIn(InternalKmpTorApi::class)
        synchronized (stateLock) {
            if (isDestroyed() || instance.isDestroyed()) return@synchronized

            // Instance already added
            if (_instanceStates[key] == instance) return@synchronized

            val m = LinkedHashMap<FileIDKey, IS>(_instanceStates.size + 1, 1.0f)
            m.putAll(_instanceStates)
            m[key] = instance
            _instanceStates = m.toImmutableMap()

            val displayed = displayed
            if (displayed == null) {
                // First instance to be added
                _selectorState = _selectorState.copy(displayed = key)

                instance.postUpdate()
                return@synchronized
            }

            if (!_selectorState.hasNext) {
                // Currently displayed instance was the last entry.
                _selectorState = _selectorState.copy(next = key)

                displayed.postUpdate()
            }

            // No display changes
        }
    }

    private fun removeInstanceState(instance: InstanceState<*>) {
        val key = instance.fileIDKey() as FileIDKey

        @OptIn(InternalKmpTorApi::class)
        synchronized(stateLock) {
            // Instance not present
            if (_instanceStates[key] != instance) return@synchronized

            val m = LinkedHashMap<FileIDKey, IS>(_instanceStates.size - 1, 1.0f).apply {
                for ((k, v) in _instanceStates.entries) {
                    if (v != instance) {
                        put(k, v)
                    }
                }
            }.toImmutableMap()

            val state = _selectorState

            val updateState = when (key) {
                state.previous -> {
                    // Find new previous
                    var previousKey: FileIDKey? = null
                    for (k in m.keys) {
                        if (k == state.displayed) break
                        previousKey = k
                    }
                    Executable {
                        _selectorState = state.copy(previous = previousKey)
                    }
                }
                state.displayed -> {
                    val newDisplayed = state.previous ?: state.next

                    if (newDisplayed == null) {
                        // Was the only instance
                        Executable {
                            _selectorState = state.copy(displayed = null)
                        }
                    } else {
                        var newPrevious: FileIDKey? = null
                        var newNext: FileIDKey? = null

                        var displayFound = false
                        for (k in m.keys) {
                            if (k == newDisplayed) {
                                displayFound = true
                                continue
                            }

                            if (!displayFound) {
                                newPrevious = k
                                continue
                            }

                            if (newNext == null) {
                                newNext = k
                                break
                            }
                        }

                        Executable {
                            _selectorState = SelectorState(
                                previous = newPrevious,
                                displayed = newDisplayed,
                                next = newNext,
                            )
                        }
                    }
                }
                state.next -> {
                    // Find new next
                    var takeNextKey = false
                    var newNext: FileIDKey? = null
                    for (k in m.keys) {
                        if (k == state.displayed) {
                            takeNextKey = true
                            continue
                        }

                        if (takeNextKey) {
                            newNext = k
                            break
                        }
                    }

                    Executable {
                        _selectorState = state.copy(next = newNext)
                    }
                }
                else -> {
                    // Was not previous, displayed, or next.
                    // No update needed.
                    null
                }
            }

            _instanceStates = m
            updateState?.execute()

            if (updateState != null) {
                displayed?.postUpdate()
            }
        }
    }

    private fun postStateChange(instance: InstanceState<*>) {
        val key = instance.fileIDKey() as FileIDKey
        _instanceStates[key]?.postUpdate()
    }

    private fun IS.postUpdate() {
        if (_selectorState.displayed != fileIDKey()) return
        val instance = this

        serviceChildScope.launch {
            val state = _selectorState
            if (state.displayed != fileIDKey()) return@launch
            if (instance.isDestroyed()) return@launch
            if (this@AbstractTorServiceUI.isDestroyed()) return@launch

            ensureActive()

            onRender(instance, state.hasPrevious, state.hasNext)
        }
    }

    private data class SelectorState(val previous: FileIDKey?, val displayed: FileIDKey?, val next: FileIDKey?) {
        val hasPrevious: Boolean = previous != null
        val hasNext: Boolean = next != null
    }

    private class FileIDKey(override val fid: String): FileID {

        override fun equals(other: Any?): Boolean {
            return other is FileIDKey && other.fid == fid
        }

        override fun hashCode(): Int {
            var result = 17
            result = result * 42 + this::class.hashCode()
            result = result * 42 + fid.hashCode()
            return result
        }

        override fun toString(): String = toFIDString(includeHashCode = false)
    }

    private inner class InstanceArgs(
        instanceConfig: Config,
        instanceScope: CoroutineScope,
        fid: String,
        val debugger: () -> ((() -> String) -> Unit)?,
        val observeSignalNewNym: (tag: String?, executor: OnEvent.Executor?, onEvent: OnEvent<String?>) -> Disposable.Once?,
        val processorAction: () -> Action.Processor?,
        val processorTorCmd: () -> TorCmd.Unprivileged.Processor?,
    ): Args.Instance(instanceConfig, instanceScope) {

        val key = FileIDKey(fid)
        val ui = this@AbstractTorServiceUI
    }

    public final override fun equals(other: Any?): Boolean {
        if (other !is AbstractTorServiceUI<*, *, *>) return false
        if (other::class != this::class) return false
        return other.args == args
    }

    public final override fun hashCode(): Int = args.hashCode()

    protected companion object {

        @JvmSynthetic
        internal val INIT = Any()
    }
}
