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
@file:Suppress("FunctionName")

package io.matthewnelson.kmp.tor.runtime

import io.matthewnelson.encoding.base16.Base16
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import io.matthewnelson.kmp.file.*
import io.matthewnelson.kmp.tor.common.api.ExperimentalKmpTorApi
import io.matthewnelson.kmp.tor.common.api.InternalKmpTorApi
import io.matthewnelson.kmp.tor.common.api.KmpTorDsl
import io.matthewnelson.kmp.tor.common.api.ResourceLoader
import io.matthewnelson.kmp.tor.common.core.SynchronizedObject
import io.matthewnelson.kmp.tor.common.core.synchronized
import io.matthewnelson.kmp.tor.runtime.FileID.Companion.toFIDString
import io.matthewnelson.kmp.tor.runtime.core.*
import io.matthewnelson.kmp.tor.runtime.core.config.TorConfig
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.core.util.isAvailableAsync
import io.matthewnelson.kmp.tor.runtime.internal.*
import io.matthewnelson.kmp.tor.runtime.internal.InstanceKeeper
import io.matthewnelson.kmp.tor.runtime.internal.RealTorRuntime
import io.matthewnelson.kmp.tor.runtime.internal.TorConfigGenerator
import org.kotlincrypto.random.CryptoRand
import org.kotlincrypto.random.RandomnessProcurementException
import kotlin.concurrent.Volatile
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic
import kotlin.random.Random

/**
 * Base interface for implementations that manage interaction with
 * a single tor daemon (backed by a stand-alone process instance).
 *
 * @see [Companion.Builder]
 * */
public sealed interface TorRuntime:
    Action.Processor,
    FileID,
    RuntimeEvent.Processor,
    TorCmd.Unprivileged.Processor
{

    /**
     * Returns the current [Environment] for this [TorRuntime] instance.
     * */
    public fun environment(): Environment

    /**
     * Checks if the tor process backing [TorRuntime] (if it is running)
     * has completed starting up.
     *
     * @see [RuntimeEvent.READY]
     * */
    public fun isReady(): Boolean

    /**
     * Returns the current [TorListeners] of this [TorRuntime]
     * instance.
     *
     * If tor is **not** 100% bootstrapped with network enabled,
     * [TorListeners.isEmpty] will always be true for the returned
     * instance of [TorListeners].
     *
     * @see [RuntimeEvent.LISTENERS]
     * */
    public fun listeners(): TorListeners

    /**
     * Returns the current [TorState] of this [TorRuntime] instance.
     *
     * @see [RuntimeEvent.STATE]
     * */
    public fun state(): TorState

    public companion object {

        /**
         * Opener for creating a [TorRuntime] instance.
         *
         * If a [TorRuntime] has already been created for the provided
         * [Environment], that [TorRuntime] instance will be returned.
         *
         * @param [environment] the operational environment for the instance
         * @see [TorRuntime.BuilderScope]
         * */
        @JvmStatic
        public fun Builder(
            environment: Environment,
            block: ThisBlock<BuilderScope>,
        ): TorRuntime = BuilderScope.build(environment, block)
    }

    @KmpTorDsl
    public class BuilderScope private constructor(private val environment: Environment) {

        private val config = mutableSetOf<ConfigCallback>()
        private val requiredTorEvents = mutableSetOf<TorEvent>()
        private val observersTorEvent = mutableSetOf<TorEvent.Observer>()
        private val observersRuntimeEvent = mutableSetOf<RuntimeEvent.Observer<*>>()

        /**
         * Configure a [NetworkObserver] for this [TorRuntime] instance.
         *
         * **NOTE:** This should be a singleton with **no** contextual or
         * non-singleton references (such as Android Activity Context).
         *
         * **NOTE:** If utilizing the `runtime-service` dependency, its
         * own observer implementation will be favored over this setting.
         * */
        @JvmField
        public var networkObserver: NetworkObserver = NetworkObserver.noOp()

        /**
         * Apply settings to the [TorConfig] at each startup. Multiple [block] may
         * be set, each of which will be applied to the [TorConfig.Builder]
         * before starting tor.
         *
         * [block] is always invoked from a background thread on Jvm & Native,
         * so it is safe to perform IO within the lambda.
         *
         * Any exception thrown within [block] will be propagated to the caller
         * of [Action.StartDaemon] or [Action.RestartDaemon].
         *
         * **NOTE:** This can be omitted as a minimum viable configuration
         * is always created. See [ConfigCallback.Defaults] for what
         * settings are automatically applied.
         *
         * **NOTE:** [block] should not contain any external contextual
         * references, such as Android Activity Context.
         * */
        @KmpTorDsl
        public fun config(
            block: ConfigCallback,
        ): BuilderScope {
            config.add(block)
            return this
        }

        /**
         * Add [TorEvent] that are required for your implementation. All
         * configured [TorEvent] will be set at startup when the control
         * connection is established via [TorCmd.SetEvents].
         *
         * Any subsequent calls for [TorCmd.SetEvents] during runtime will
         * be intercepted and modified to include all required [TorEvent].
         *
         * **NOTE:** [TorEvent.CONF_CHANGED] and [TorEvent.NOTICE] will always
         * be present as it is required for the [TorRuntime] implementation.
         * They need not be added here.
         * */
        @KmpTorDsl
        public fun required(
            event: TorEvent,
        ): BuilderScope {
            requiredTorEvents.add(event)
            return this
        }

        /**
         * Add [TorEvent.Observer] which is non-static (can be removed at any time).
         *
         * @see [observerStatic]
         * */
        @KmpTorDsl
        public fun observer(
            observer: TorEvent.Observer,
        ): BuilderScope {
            observersTorEvent.add(observer)
            return this
        }

        /**
         * Add [TorEvent.Observer] which will never be removed from [TorRuntime].
         *
         * Useful for logging purposes.
         * */
        @KmpTorDsl
        public fun observerStatic(
            event: TorEvent,
            onEvent: OnEvent<String>,
        ): BuilderScope = observerStatic(event, null, onEvent)

        /**
         * Add [TorEvent.Observer] which will never be removed from [TorRuntime].
         *
         * Useful for logging purposes.
         * */
        @KmpTorDsl
        public fun observerStatic(
            event: TorEvent,
            executor: OnEvent.Executor?,
            onEvent: OnEvent<String>,
        ): BuilderScope = observer(TorEvent.Observer(event, environment.staticTag(), executor, onEvent))

        /**
         * Add [RuntimeEvent.Observer] which is non-static (can be removed at any time).
         *
         * @see [observerStatic]
         * */
        @KmpTorDsl
        public fun <R: Any> observer(
            observer: RuntimeEvent.Observer<R>,
        ): BuilderScope {
            observersRuntimeEvent.add(observer)
            return this
        }

        /**
         * Add [RuntimeEvent.Observer] which will never be removed from [TorRuntime].
         *
         * Useful for logging purposes.
         * */
        @KmpTorDsl
        public fun <R: Any> observerStatic(
            event: RuntimeEvent<R>,
            onEvent: OnEvent<R>,
        ): BuilderScope = observerStatic(event, null, onEvent)

        /**
         * Add [RuntimeEvent.Observer] which will never be removed from [TorRuntime].
         *
         * Useful for logging purposes.
         * */
        @KmpTorDsl
        public fun <R: Any> observerStatic(
            event: RuntimeEvent<R>,
            executor: OnEvent.Executor?,
            onEvent: OnEvent<R>,
        ): BuilderScope = observer(RuntimeEvent.Observer(event, environment.staticTag(), executor, onEvent))

        internal companion object: InstanceKeeper<String, TorRuntime>() {

            @JvmSynthetic
            internal fun build(
                environment: Environment,
                block: ThisBlock<BuilderScope>,
            ): TorRuntime {
                val b = BuilderScope(environment).apply(block)

                return getOrCreateInstance(key = environment.fid, block = {

                    val generator = TorConfigGenerator(
                        environment = environment,
                        config = b.config,
                        isPortAvailable = { host, port -> port.isAvailableAsync(host) },
                    )

                    RealTorRuntime.of(
                        generator = generator,
                        networkObserver = b.networkObserver,
                        requiredTorEvents = b.requiredTorEvents,
                        observersTorEvent = b.observersTorEvent,
                        observersRuntimeEvent = b.observersRuntimeEvent,
                    )
                })
            }
        }
    }

    /**
     * The environment for which [TorRuntime] operates.
     *
     * Specified directories/files are utilized by [TorRuntime.BuilderScope.config]
     * to create a minimum viable [TorConfig].
     *
     * The [Environment] API is mostly based around configuration options that
     * are, or could be, platform-specific. This means less platform-specific
     * code, and more `commonMain` code.
     *
     * @see [Companion.Builder]
     * */
    public class Environment private constructor(
        @JvmField
        public val workDirectory: File,
        @JvmField
        public val cacheDirectory: File,
        @JvmField
        public val loader: ResourceLoader.Tor,

        private val _defaultExecutor: OnEvent.Executor,
        @OptIn(ExperimentalKmpTorApi::class)
        private val _serviceFactoryLoader: ServiceFactory.Loader?,
    ): FileID {

        /**
         * Toggle to dispatch [RuntimeEvent.LOG.DEBUG] and [TorEvent.DEBUG]
         * or not.
         *
         * **NOTE:** This does not alter control connection event listeners
         * via [TorCmd.SetEvents]. Add [TorEvent.DEBUG] via
         * [TorRuntime.BuilderScope.required] if debug logs from tor are needed.
         *
         * **NOTE:** Debug logs may reveal sensitive information and should
         * not be enabled in production!
         * */
        @JvmField
        @Volatile
        public var debug: Boolean = false

        public override val fid: String by lazy { FileID.createFID(workDirectory) }

        public companion object {

            /**
             * Opener for creating an [Environment] instance.
             *
             * [workDirectory] should be specified within your application's home
             * directory (e.g. `$HOME/.my_application/torservice`). This will
             * be utilized as the tor process' `HOME` environment variable (if using
             * a [ResourceLoader.Tor.Exec]).
             *
             * [cacheDirectory] should be specified within your application's cache
             * directory (e.g. `$HOME/.my_application/cache/torservice`).
             *
             * It is advisable to keep the dirname for [workDirectory] and [cacheDirectory]
             * identical (e.g. `torservice`), especially when creating multiple
             * instances of [Environment].
             *
             * **NOTE:** If an [Environment] already exists for the provided [workDirectory]
             * **or** [cacheDirectory], that instance will be returned.
             *
             * @param [workDirectory] tor's working directory (e.g. `$HOME/.my_application/torservice`)
             *   This will be utilized as the tor process' `HOME` environment variable (if using [ResourceLoader.Tor.Exec]).
             * @param [cacheDirectory] tor's cache directory (e.g. `$HOME/.my_application/cache/torservice`).
             * @param [loader] lambda for creating [ResourceLoader.Tor] using the configured
             *   [BuilderScope.resourceDir]. See [kmp-tor-resource](https://github.com/05nelsonm/kmp-tor-resource)
             * @throws [IllegalArgumentException] when [workDirectory] and [cacheDirectory] are
             *   the same.
             * */
            @JvmStatic
            public fun Builder(
                workDirectory: File,
                cacheDirectory: File,
                loader: (resourceDir: File) -> ResourceLoader.Tor,
            ): Environment = BuilderScope.build(workDirectory, cacheDirectory, loader, null)

            /**
             * Opener for creating an [Environment] instance.
             *
             * [workDirectory] should be specified within your application's home
             * directory (e.g. `$HOME/.my_application/torservice`). This will
             * be utilized as the tor process' `HOME` environment variable (if using
             * a [ResourceLoader.Tor.Exec]).
             *
             * [cacheDirectory] should be specified within your application's cache
             * directory (e.g. `$HOME/.my_application/cache/torservice`).
             *
             * It is advisable to keep the dirname for [workDirectory] and [cacheDirectory]
             * identical (e.g. `torservice`), especially when creating multiple
             * instances of [Environment].
             *
             * **NOTE:** If an [Environment] already exists for the provided [workDirectory]
             * **or** [cacheDirectory], that instance will be returned.
             *
             * @param [workDirectory] tor's working directory (e.g. `$HOME/.my_application/torservice`)
             *   This will be utilized as the tor process' `HOME` environment variable (if using [ResourceLoader.Tor.Exec]).
             * @param [cacheDirectory] tor's cache directory (e.g. `$HOME/.my_application/cache/torservice`).
             * @param [loader] lambda for creating [ResourceLoader.Tor] using the configured
             *   [BuilderScope.resourceDir]. See [kmp-tor-resource](https://github.com/05nelsonm/kmp-tor-resource)
             * @param [block] optional lambda for modifying default parameters.
             * @throws [IllegalArgumentException] when [workDirectory] and [cacheDirectory] are
             *   the same.
             * */
            @JvmStatic
            public fun Builder(
                workDirectory: File,
                cacheDirectory: File,
                loader: (resourceDir: File) -> ResourceLoader.Tor,
                block: ThisBlock<BuilderScope>,
            ): Environment = BuilderScope.build(workDirectory, cacheDirectory, loader, block)
        }

        @KmpTorDsl
        public class BuilderScope private constructor(
            @JvmField
            public val workDirectory: File,
            @JvmField
            public val cacheDirectory: File,
        ) {

            /**
             * Declare a default [OnEvent.Executor] to utilize when notifying
             * subscribed [TorEvent] and [RuntimeEvent] observers (if the
             * observer did not declare its own).
             *
             * This default can be overridden on a per-observer basis when
             * creating them, given the needs of that observer and how it is
             * being used and/or implemented.
             *
             * **NOTE:** [OnEvent.Executor] should be a singleton with **no**
             * contextual or non-singleton references outside the
             * [OnEvent.Executor.execute] lambda.
             *
             * Default: If [OnEvent.Executor.Main.isAvailable] is `true`, will
             * use [OnEvent.Executor.Main], otherwise [OnEvent.Executor.Immediate].
             *
             * @see [OnEvent.Executor]
             * */
            @JvmField
            public var defaultEventExecutor: OnEvent.Executor = if (OnEvent.Executor.Main.isAvailable) {
                OnEvent.Executor.Main
            } else {
                OnEvent.Executor.Immediate
            }

            /**
             * The directory for which [ResourceLoader.Tor] will be created with. This
             * is a convenience function, as only a single instance of [ResourceLoader.Tor]
             * can ever be created per application process.
             *
             * Default: [workDirectory]
             * */
            @JvmField
            public var resourceDir: File = workDirectory

            /**
             * Experimental support for running tor as a service. Currently
             * only Android support is available via the `runtime-service`
             * dependency. This setting is automatically configured if using
             * `runtime-service` dependency and utilizing the extended builder
             * functions [io.matthewnelson.kmp.tor.runtime.service.TorServiceConfig]
             * */
            @JvmField
            @ExperimentalKmpTorApi
            public var serviceFactoryLoader: ServiceFactory.Loader? = null

            internal companion object: InstanceKeeper<EnvironmentKey, Environment>() {

                @JvmSynthetic
                @OptIn(ExperimentalKmpTorApi::class)
                internal fun build(
                    workDirectory: File,
                    cacheDirectory: File,
                    loader: (resourceDir: File) -> ResourceLoader.Tor,
                    block: ThisBlock<BuilderScope>?,
                ): Environment {
                    val b = BuilderScope(workDirectory.absoluteFile.normalize(), cacheDirectory.absoluteFile.normalize())

                    require(b.workDirectory != b.cacheDirectory) {
                        "workDirectory and cacheDirectory cannot be the same locations"
                    }

                    // Apply block outside getOrCreateInstance call to
                    // prevent double instance creation
                    if (block != null) b.apply(block)

                    // Clear loader reference from builder so if the builder
                    // reference was held outside block lambda it will always
                    // be null.
                    val serviceLoader = b.serviceFactoryLoader
                    b.serviceFactoryLoader = null

                    val resourceLoader = loader(b.resourceDir.absoluteFile.normalize())

                    val key = EnvironmentKey(b.workDirectory, b.cacheDirectory)

                    return getOrCreateInstance(key = key, block = block@ { existing ->
                        // If Environment already exists for NoExec, return
                        // that instance. Multi-Instance support is only available
                        // for Exec (process creation).
                        if (resourceLoader is ResourceLoader.Tor.NoExec) {
                            if (existing.isNotEmpty()) {
                                return@block existing.first().second
                            }
                        }

                        Environment(
                            workDirectory = b.workDirectory,
                            cacheDirectory = b.cacheDirectory,
                            loader = resourceLoader,
                            _defaultExecutor = b.defaultEventExecutor,
                            _serviceFactoryLoader = serviceLoader,
                        )
                    })
                }
            }
        }

        private val _staticTag: String by lazy {
            val buf = ByteArray(16)
            try {
                CryptoRand.Default.nextBytes(buf)
            } catch (_: RandomnessProcurementException) {
                Random.Default.nextBytes(buf)
            }
            buf.encodeToString(Base16)
        }

        @JvmSynthetic
        internal fun staticTag(): String = _staticTag

        @JvmSynthetic
        internal fun defaultExecutor(): OnEvent.Executor = _defaultExecutor

        @JvmSynthetic
        @OptIn(ExperimentalKmpTorApi::class)
        internal fun serviceFactoryLoader(): ServiceFactory.Loader? = _serviceFactoryLoader

        /** @suppress */
        public override fun equals(other: Any?): Boolean = other is Environment && other.fid == fid
        /** @suppress */
        public override fun hashCode(): Int = 17 * 31 + fid.hashCode()
        /** @suppress */
        public override fun toString(): String = toFIDString(includeHashCode = false)
    }

    /**
     * An instance of [TorRuntime] that produces [Lifecycle.DestroyableTorRuntime]
     * under the hood which are intended to be run within a service object.
     *
     * **NOTE:** This and its subclasses are currently marked as [ExperimentalKmpTorApi].
     * Things may change (as the annotation states), so use at your own risk! Prefer
     * using the stable implementation via the `kmp-tor:runtime-service` dependency.
     *
     * @see [Lifecycle.DestroyableTorRuntime]
     * */
    @ExperimentalKmpTorApi
    public abstract class ServiceFactory protected constructor(initializer: Initializer): TorRuntime {

        /**
         * Helper for loading the implementation of [ServiceFactory].
         *
         * @see [Environment.BuilderScope.serviceFactoryLoader]
         * */
        @ExperimentalKmpTorApi
        public abstract class Loader {

            protected abstract fun loadProtected(initializer: Initializer): ServiceFactory

            @JvmSynthetic
            internal fun load(initializer: Initializer): ServiceFactory = loadProtected(initializer)
        }

        /**
         * Single use class for initializing [ServiceFactory]. Multiples uses will
         * result in [IllegalStateException] as the [ServiceFactory] implementation
         * is held as a singleton for the given [Environment] that it belongs to.
         * */
        @ExperimentalKmpTorApi
        public class Initializer private constructor(
            private val create: (startService: () -> Unit) -> ServiceFactoryDriver,
        ) {

            @Volatile
            private var _isInitialized: Boolean = false
            @OptIn(InternalKmpTorApi::class)
            private val lock = SynchronizedObject()

            @JvmSynthetic
            @OptIn(InternalKmpTorApi::class)
            @Throws(IllegalStateException::class)
            internal fun initialize(
                startService: () -> Unit,
            ): ServiceFactoryDriver = synchronized(lock) {
                check(!_isInitialized) {
                    "TorRuntime.ServiceFactory.Initializer can only be initialized once"
                }

                _isInitialized = true
                create(startService)
            }

            internal companion object {

                @JvmSynthetic
                internal fun of(
                    create: (startService: () -> Unit) -> ServiceFactoryDriver,
                ): Initializer = Initializer(create)
            }
        }

        /**
         * Helper for service objects to bind to the [ServiceFactory] by creating
         * an instance of [Lifecycle.DestroyableTorRuntime].
         *
         * @see [ServiceFactory.binder]
         * */
        @ExperimentalKmpTorApi
        public interface Binder: RuntimeEvent.Notifier, FileID {

            /**
             * Meant to be called within the service object instance after
             * [startService] has successfully executed, and [ServiceFactory.binder]
             * has been injected into it.
             *
             * The returned instance of [Lifecycle.DestroyableTorRuntime] will
             * invoke any [Lifecycle.DestroyableTorRuntime.invokeOnDestroy] handles
             * upon processing of [Action.StopDaemon] such that the service object
             * can attach one and know when to shut itself down.
             *
             * If an instance of [Lifecycle.DestroyableTorRuntime] already exists
             * for the calling [ServiceFactory], that instance will be destroyed
             * before a new instance of [Lifecycle.DestroyableTorRuntime] is returned.
             *
             * **WARNING:** This should not be called without first enqueueing
             * [Action.StartDaemon] or [Action.RestartDaemon]. If no action job is
             * present when [onBind] is called from [ServiceFactory.binder], then
             * [Action.StartDaemon] will be enqueued for you. If the execution of
             * that job results in failure an [UncaughtException] may occur.
             * */
            public fun onBind(
                serviceEvents: Set<TorEvent>,
                serviceObserverNetwork: NetworkObserver?,
                serviceObserversTorEvent: Set<TorEvent.Observer>,
                serviceObserversRuntimeEvent: Set<RuntimeEvent.Observer<*>>,
            ): Lifecycle.DestroyableTorRuntime
        }

        /**
         * Called when [Action.StartDaemon] or [Action.RestartDaemon] has been
         * enqueued and an instance of [Lifecycle.DestroyableTorRuntime] does
         * not currently exist for this [ServiceFactory].
         *
         * Implementors of [ServiceFactory.startService] must start the service
         * and call [Binder.onBind] from the injected [binder] reference within
         * 1_000ms, otherwise a timeout will occur and all enqueued jobs waiting
         * to be handed off to [Lifecycle.DestroyableTorRuntime] for completion
         * will be terminated.
         *
         * @throws [RuntimeException] if there was an error trying to start the service.
         * */
        @Throws(RuntimeException::class)
        protected abstract fun startService()

        private val driver: ServiceFactoryDriver = initializer.initialize(::startService)

        @JvmField
        protected val binder: Binder = driver.binder

        public final override val fid: String = driver.fid
        public final override fun environment(): Environment = driver.environment()
        public final override fun isReady(): Boolean = driver.isReady()
        public final override fun listeners(): TorListeners = driver.listeners()
        public final override fun state(): TorState = driver.state()

        public final override fun <Success: Any> enqueue(
            cmd: TorCmd.Unprivileged<Success>,
            onFailure: OnFailure,
            onSuccess: OnSuccess<Success>,
        ): EnqueuedJob = driver.enqueue(cmd, onFailure, onSuccess)

        public final override fun enqueue(
            action: Action,
            onFailure: OnFailure,
            onSuccess: OnSuccess<Unit>,
        ): EnqueuedJob = driver.enqueue(action, onFailure, onSuccess)

        public final override fun subscribe(observer: TorEvent.Observer) {
            driver.subscribe(observer)
        }

        public final override fun subscribe(vararg observers: TorEvent.Observer) {
            driver.subscribe(*observers)
        }

        public final override fun subscribe(observer: RuntimeEvent.Observer<*>) {
            driver.subscribe(observer)
        }

        public final override fun subscribe(vararg observers: RuntimeEvent.Observer<*>) {
            driver.subscribe(*observers)
        }

        public final override fun unsubscribe(observer: TorEvent.Observer) {
            driver.unsubscribe(observer)
        }

        public final override fun unsubscribe(vararg observers: TorEvent.Observer) {
            driver.unsubscribe(*observers)
        }

        public final override fun unsubscribe(observer: RuntimeEvent.Observer<*>) {
            driver.unsubscribe(observer)
        }

        public final override fun unsubscribe(vararg observers: RuntimeEvent.Observer<*>) {
            driver.unsubscribe(*observers)
        }

        public final override fun unsubscribeAll(event: TorEvent) {
            driver.unsubscribeAll(event)
        }

        public final override fun unsubscribeAll(vararg events: TorEvent) {
            driver.unsubscribeAll(*events)
        }

        public final override fun unsubscribeAll(tag: String) {
            driver.unsubscribeAll(tag)
        }

        public final override fun unsubscribeAll(event: RuntimeEvent<*>) {
            driver.unsubscribeAll(event)
        }

        public final override fun unsubscribeAll(vararg events: RuntimeEvent<*>) {
            driver.unsubscribeAll(*events)
        }

        public final override fun clearObservers() {
            driver.clearObservers()
        }

        /** @suppress */
        public final override fun equals(other: Any?): Boolean = other is ServiceFactory && other.driver == driver
        /** @suppress */
        public final override fun hashCode(): Int = driver.hashCode()
        /** @suppress */
        public final override fun toString(): String = toFIDString(defaultClassName = "ServiceFactory", includeHashCode = false)
    }
}
