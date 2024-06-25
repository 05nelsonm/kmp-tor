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
import io.matthewnelson.immutable.collections.toImmutableMap
import io.matthewnelson.kmp.file.*
import io.matthewnelson.kmp.tor.core.api.ResourceInstaller
import io.matthewnelson.kmp.tor.core.api.ResourceInstaller.Paths
import io.matthewnelson.kmp.tor.core.api.annotation.ExperimentalKmpTorApi
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.api.annotation.KmpTorDsl
import io.matthewnelson.kmp.tor.core.resource.SynchronizedObject
import io.matthewnelson.kmp.tor.core.resource.synchronized
import io.matthewnelson.kmp.tor.runtime.FileID.Companion.toFIDString
import io.matthewnelson.kmp.tor.runtime.TorRuntime.Companion.Builder
import io.matthewnelson.kmp.tor.runtime.TorRuntime.Environment.Companion.Builder
import io.matthewnelson.kmp.tor.runtime.core.*
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.core.util.isAvailableAsync
import io.matthewnelson.kmp.tor.runtime.internal.*
import io.matthewnelson.kmp.tor.runtime.internal.InstanceKeeper
import io.matthewnelson.kmp.tor.runtime.internal.RealTorRuntime
import io.matthewnelson.kmp.tor.runtime.internal.TorConfigGenerator
import kotlinx.coroutines.Dispatchers
import org.kotlincrypto.SecRandomCopyException
import org.kotlincrypto.SecureRandom
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
    TorCmd.Unprivileged.Processor,
    TorEvent.Processor
{

    /**
     * Returns the current [Environment] for this [TorRuntime] instance.
     * */
    public fun environment(): Environment

    /**
     * Checks if the tor process backing [TorRuntime] (if it is running)
     * has completed starting up.
     *
     * @see [RuntimeEvent.PROCESS.READY]
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
         * @see [TorRuntime.Builder]
         * */
        @JvmStatic
        public fun Builder(
            environment: Environment,
            block: ThisBlock<Builder>,
        ): TorRuntime = Builder.build(environment, block)
    }

    @KmpTorDsl
    public class Builder private constructor(private val environment: Environment) {

        private val config = mutableSetOf<ConfigBuilderCallback>()
        private val requiredTorEvents = mutableSetOf<TorEvent>()
        private val observersTorEvent = mutableSetOf<TorEvent.Observer>()
        private val observersRuntimeEvent = mutableSetOf<RuntimeEvent.Observer<*>>()

        /**
         * Configure a [NetworkObserver] for this [TorRuntime] instance.
         *
         * **NOTE:** This should be a singleton with **no** contextual or
         * non-singleton references (such as Android Activity Context).
         *
         * **NOTE:** If utilizing the `kmp-tor-mobile` dependency, its
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
         * is always created. See [ConfigBuilderCallback.putDefaults] for what
         * settings are automatically applied.
         *
         * **NOTE:** [block] should not contain any external contextual
         * references, such as Android Activity Context.
         * */
        @KmpTorDsl
        public fun config(
            block: ConfigBuilderCallback,
        ): Builder {
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
         * **NOTE:** [TorEvent.CONF_CHANGED] will always present as it is
         * required for the [TorRuntime] implementation. It does not need to
         * be added here.
         * */
        @KmpTorDsl
        public fun required(
            event: TorEvent,
        ): Builder {
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
        ): Builder {
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
        ): Builder = observerStatic(event, null, onEvent)

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
        ): Builder = observer(TorEvent.Observer(event, environment.staticTag(), executor, onEvent))

        /**
         * Add [RuntimeEvent.Observer] which is non-static (can be removed at any time).
         *
         * @see [observerStatic]
         * */
        @KmpTorDsl
        public fun <R: Any> observer(
            observer: RuntimeEvent.Observer<R>,
        ): Builder {
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
        ): Builder = observerStatic(event, null, onEvent)

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
        ): Builder = observer(RuntimeEvent.Observer(event, environment.staticTag(), executor, onEvent))

        internal companion object: InstanceKeeper<String, TorRuntime>() {

            @JvmSynthetic
            internal fun build(
                environment: Environment,
                block: ThisBlock<Builder>,
            ): TorRuntime {
                val b = Builder(environment).apply(block)

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
     * Specified directories/files are utilized by [TorRuntime.Builder.config]
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
        public val omitGeoIPFileSettings: Boolean,
        @JvmField
        public val torResource: ResourceInstaller<Paths.Tor>,
        @JvmField
        public val processEnv: Map<String, String>,

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
         * [TorRuntime.Builder.required] if debug logs from tor are needed.
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
             * be utilized as the tor process' `HOME` environment variable.
             *
             * [cacheDirectory] should be specified within your application's cache
             * directory (e.g. `$HOME/.my_application/cache/torservice`).
             *
             * It is advisable to keep the dirname for [workDirectory] and [cacheDirectory]
             * identical (e.g. `torservice`), especially when creating multiple
             * instances of [Environment].
             *
             * **NOTE:** If the same directory is utilized for both [workDirectory]
             * and [cacheDirectory], tor may fail to start; they **must** be different.
             *
             * **NOTE:** If an [Environment] already exists for the provided [workDirectory]
             * **or** [cacheDirectory], that instance will be returned.
             *
             * @param [workDirectory] tor's working directory (e.g. `$HOME/.my_application/torservice`)
             *   This will be utilized as the tor process' `HOME` environment variable.
             * @param [cacheDirectory] tor's cache directory (e.g. `$HOME/.my_application/cache/torservice`).
             * @param [installer] lambda for creating [ResourceInstaller] using the configured
             *   [Builder.installationDirectory]. See [kmp-tor-resource](https://github.com/05nelsonm/kmp-tor-resource)
             * @see [io.matthewnelson.kmp.tor.runtime.service.createTorRuntimeEnvironment]
             * */
            @JvmStatic
            public fun Builder(
                workDirectory: File,
                cacheDirectory: File,
                installer: (installationDirectory: File) -> ResourceInstaller<Paths.Tor>,
            ): Environment = Builder.build(workDirectory, cacheDirectory, installer, null)

            /**
             * Opener for creating an [Environment] instance.
             *
             * [workDirectory] should be specified within your application's home
             * directory (e.g. `$HOME/.my_application/torservice`). This will
             * be utilized as the tor process' `HOME` environment variable.
             *
             * [cacheDirectory] should be specified within your application's cache
             * directory (e.g. `$HOME/.my_application/cache/torservice`).
             *
             * It is advisable to keep the dirname for [workDirectory] and [cacheDirectory]
             * identical (e.g. `torservice`), especially when creating multiple
             * instances of [Environment].
             *
             * **NOTE:** If the same directory is utilized for both [workDirectory]
             * and [cacheDirectory], tor may fail to start; they **must** be different.
             *
             * **NOTE:** If an [Environment] already exists for the provided [workDirectory]
             * **or** [cacheDirectory], that instance will be returned.
             *
             * @param [workDirectory] tor's working directory (e.g. `$HOME/.my_application/torservice`)
             *   This will be utilized as the tor process' `HOME` environment variable.
             * @param [cacheDirectory] tor's cache directory (e.g. `$HOME/.my_application/cache/torservice`).
             * @param [installer] lambda for creating [ResourceInstaller] using the configured
             *   [Builder.installationDirectory]. See [kmp-tor-resource](https://github.com/05nelsonm/kmp-tor-resource)
             * @param [block] optional lambda for modifying default parameters.
             * @see [io.matthewnelson.kmp.tor.runtime.service.createTorRuntimeEnvironment]
             * */
            @JvmStatic
            public fun Builder(
                workDirectory: File,
                cacheDirectory: File,
                installer: (installationDirectory: File) -> ResourceInstaller<Paths.Tor>,
                block: ThisBlock<Builder>,
            ): Environment = Builder.build(workDirectory, cacheDirectory, installer, block)
        }

        @KmpTorDsl
        public class Builder private constructor(
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
             * If true, [TorConfig.GeoIPFile] and [TorConfig.GeoIPv6File] will **not**
             * be automatically added via [TorRuntime.Builder.config] using paths
             * returned from [ResourceInstaller.install].
             *
             * This is useful if an alternative installation of tor is being used from
             * [kmp-tor-resource](https://github.com/05nelsonm/kmp-tor-resource).
             *
             * Default: `false`
             * */
            @JvmField
            public var omitGeoIPFileSettings: Boolean = false

            /**
             * The directory for which **all** resources will be installed.
             *
             * Default: [workDirectory]
             *
             * **NOTE:** If the same [installationDirectory] is defined for
             * multiple [Environment] instances, the same [ResourceInstaller]
             * instance will be utilized across those [Environment]. This helps
             * mitigate unnecessary resource extraction when running multiple
             * instances of [TorRuntime].
             * */
            @JvmField
            public var installationDirectory: File = workDirectory

            /**
             * Customization of environment variables for the tor process.
             *
             * **NOTE:** The `HOME` environment variable is **always** set to [workDirectory].
             * */
            @JvmField
            @ExperimentalKmpTorApi
            public val processEnv: LinkedHashMap<String, String> = LinkedHashMap(1, 1.0F)

            init {
                @OptIn(ExperimentalKmpTorApi::class)
                processEnv["HOME"] = workDirectory.path
            }

            /**
             * Experimental support for running tor as a service. Currently
             * only Android support is available via the `runtime-service`
             * dependency. This setting is automatically configured if using
             * `runtime-service` dependency and utilizing the extended builder
             * functions [io.matthewnelson.kmp.tor.runtime.service.createTorRuntimeEnvironment]
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
                    installer: (installationDirectory: File) -> ResourceInstaller<Paths.Tor>,
                    block: ThisBlock<Builder>?,
                ): Environment {
                    val b = Builder(workDirectory.absoluteFile.normalize(), cacheDirectory.absoluteFile.normalize())
                    // Apply block outside getOrCreateInstance call to
                    // prevent double instance creation
                    if (block != null) b.apply(block)

                    // Clear loader reference from builder so if the builder
                    // reference was held outside block lambda it will always
                    // be null.
                    val loader = b.serviceFactoryLoader
                    b.serviceFactoryLoader = null

                    var torResource = installer(b.installationDirectory.absoluteFile.normalize())

                    val key = EnvironmentKey(b.workDirectory, b.cacheDirectory)

                    return getOrCreateInstance(key = key, block = { instances ->

                        for (other in instances) {
                            val otherTorResource = other.second.torResource
                            if (otherTorResource.installationDir == torResource.installationDir) {
                                torResource = otherTorResource
                                break
                            }
                        }

                        // Always reset, just in case it was overridden
                        b.processEnv["HOME"] = b.workDirectory.path

                        Environment(
                            workDirectory = b.workDirectory,
                            cacheDirectory = b.cacheDirectory,
                            omitGeoIPFileSettings = b.omitGeoIPFileSettings,
                            torResource = torResource,
                            processEnv = b.processEnv.toImmutableMap(),
                            _defaultExecutor = b.defaultEventExecutor,
                            _serviceFactoryLoader = loader,
                        )
                    })
                }
            }
        }

        private val _staticTag: String by lazy {
            try {
                SecureRandom().nextBytesOf(16)
            } catch (_: SecRandomCopyException) {
                Random.Default.nextBytes(16)
            }.encodeToString(Base16)
        }

        @JvmSynthetic
        internal fun staticTag(): String = _staticTag

        @JvmSynthetic
        internal fun defaultExecutor(): OnEvent.Executor = _defaultExecutor

        @JvmSynthetic
        @OptIn(ExperimentalKmpTorApi::class)
        internal fun serviceFactoryLoader(): ServiceFactory.Loader? = _serviceFactoryLoader

        public override fun equals(other: Any?): Boolean = other is Environment && other.fid == fid
        public override fun hashCode(): Int = 17 * 31 + fid.hashCode()
        public override fun toString(): String = toFIDString(includeHashCode = false)
    }

    /**
     * An instance of [TorRuntime] which produces [Lifecycle.DestroyableTorRuntime]
     * under the hood which are intended to be run within a service object.
     *
     * **NOTE:** This is currently an [ExperimentalKmpTorApi], only being implemented
     * by the `kmp-tor:runtime-service` dependency. Things may change, so use at your
     * own risk!
     *
     * @see [Lifecycle.DestroyableTorRuntime]
     * */
    @ExperimentalKmpTorApi
    public abstract class ServiceFactory protected constructor(initializer: Initializer): TorRuntime {

        /**
         * Helper for loading the implementation of [ServiceFactory].
         *
         * @see [Environment.Builder.serviceFactoryLoader]
         * */
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
         * 500ms, otherwise a timeout will occur and all enqueued jobs waiting
         * to be handed off to [Lifecycle.DestroyableTorRuntime] for completion
         * will be terminated.
         *
         * **NOTE:** If [Dispatchers.Main] is available, [startService] will always
         * be called from the main thread.
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

        public final override fun equals(other: Any?): Boolean = other is ServiceFactory && other.driver == driver
        public final override fun hashCode(): Int = driver.hashCode()
        public final override fun toString(): String = toFIDString(defaultClassName = "ServiceFactory", includeHashCode = false)
    }
}
