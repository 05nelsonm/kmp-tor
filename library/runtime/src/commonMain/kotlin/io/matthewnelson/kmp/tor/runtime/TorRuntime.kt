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
import io.matthewnelson.immutable.collections.toImmutableSet
import io.matthewnelson.kmp.file.*
import io.matthewnelson.kmp.tor.core.api.ResourceInstaller
import io.matthewnelson.kmp.tor.core.api.ResourceInstaller.Paths
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.api.annotation.KmpTorDsl
import io.matthewnelson.kmp.tor.runtime.FileID.Companion.toFIDString
import io.matthewnelson.kmp.tor.runtime.TorRuntime.Companion.Builder
import io.matthewnelson.kmp.tor.runtime.TorRuntime.Environment.Companion.Builder
import io.matthewnelson.kmp.tor.runtime.core.*
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.core.util.isAvailableAsync
import io.matthewnelson.kmp.tor.runtime.internal.InstanceKeeper
import io.matthewnelson.kmp.tor.runtime.internal.RealTorRuntime
import io.matthewnelson.kmp.tor.runtime.internal.RealTorRuntime.Companion.checkInstance
import io.matthewnelson.kmp.tor.runtime.internal.TorConfigGenerator
import org.kotlincrypto.SecRandomCopyException
import org.kotlincrypto.SecureRandom
import kotlin.concurrent.Volatile
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic
import kotlin.random.Random

/**
 * Base interface for managing and interacting with tor.
 *
 * @see [Companion.Builder]
 * */
public interface TorRuntime:
    FileID,
    TorCmd.Unprivileged.Processor,
    TorEvent.Processor,
    RuntimeAction.Processor,
    RuntimeEvent.Processor
{

    /**
     * Returns the current [Environment] for this [TorRuntime] instance.
     * */
    public fun environment(): Environment

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
        private val requiredTorEvents = mutableSetOf(TorEvent.CONF_CHANGED, TorEvent.NOTICE)
        private val observersTorEvent = mutableSetOf<TorEvent.Observer>()
        private val observersRuntimeEvent = mutableSetOf<RuntimeEvent.Observer<*>>()

        /**
         * If true, [Paths.Tor.geoip] and [Paths.Tor.geoip6] will **not** be
         * automatically added to your [TorConfig].
         *
         * Useful if using [TorRuntime] with a system installation of tor (such
         * as a Docker container) instead of
         * [kmp-tor-resource](https://github.com/05nelsonm/kmp-tor-resource)
         * */
        @JvmField
        public var omitGeoIPFileSettings: Boolean = false

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
        public var networkObserver: NetworkObserver = NetworkObserver.NOOP

        /**
         * Declare a default [OnEvent.Executor] to utilize when dispatching
         * [TorEvent] and [RuntimeEvent] to registered observers (if the observer
         * does not provide its own). This can be overridden on a per-observer
         * basis when creating them, given the needs of that observer and how it
         * is being used and/or implemented.
         *
         * **NOTE:** This should be a singleton with **no** contextual or
         * non-singleton references outside the [OnEvent.Executor.execute]
         * lambda.
         *
         * Default: [OnEvent.Executor.Immediate]
         * */
        @JvmField
        public var defaultEventExecutor: OnEvent.Executor = OnEvent.Executor.Immediate

        /**
         * Apply settings to the [TorConfig] at each startup. Multiple [block] may
         * be set, each of which will be applied to the [TorConfig.Builder]
         * before starting tor.
         *
         * [block] is always invoked from a background thread on Jvm & Native,
         * so it is safe to perform IO within the lambda (e.g. writing settings
         * that are not currently supported to the [Environment.torrcFile]).
         *
         * Any exception thrown within [block] will be propagated to the caller
         * of [RuntimeAction.StartDaemon] or [RuntimeAction.RestartDaemon].
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
        ): Builder = observer(TorEvent.Observer(event, environment.staticTag, executor, onEvent))

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
        ): Builder = observer(RuntimeEvent.Observer(event, environment.staticTag, executor, onEvent))

        internal companion object: InstanceKeeper<File, TorRuntime>() {

            @JvmSynthetic
            internal fun build(
                environment: Environment,
                block: ThisBlock<Builder>,
            ): TorRuntime {
                val b = Builder(environment).apply(block)

                return getOrCreateInstance(key = environment.workDir, block = {

                    val generator = TorConfigGenerator(
                        environment = environment,
                        omitGeoIPFileSettings = b.omitGeoIPFileSettings,
                        config = b.config.toImmutableSet(),
                        isPortAvailable = { host, port -> port.isAvailableAsync(host) },
                    )

                    RealTorRuntime.of(
                        generator = generator,
                        networkObserver = b.networkObserver,
                        requiredTorEvents = b.requiredTorEvents.toImmutableSet(),
                        observersTorEvent = b.observersTorEvent.toImmutableSet(),
                        defaultExecutor = b.defaultEventExecutor,
                        observersRuntimeEvent = b.observersRuntimeEvent.toImmutableSet(),
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
     * @see [Companion.Builder]
     * */
    public class Environment private constructor(
        @JvmField
        public val workDir: File,
        @JvmField
        public val cacheDir: File,
        @JvmField
        public val torrcFile: File,
        @JvmField
        public val torrcDefaultsFile: File,
        @JvmField
        public val torResource: ResourceInstaller<Paths.Tor>,
    ): FileID {

        /**
         * Toggle to dispatch [RuntimeEvent.LOG.DEBUG] and [TorEvent.DEBUG]
         * or not.
         *
         * **NOTE:** This does not alter control connection event listeners
         * via [TorCmd.SetEvents]. Add [TorEvent.DEBUG] via
         * [TorRuntime.Builder.required] if debug logs from tor are needed.
         *
         * **NOTE:** Debug logs may reveal sensitive information
         * and should not be enabled in production!
         * */
        @JvmField
        @Volatile
        public var debug: Boolean = false

        public override val fid: String by lazy { FileID.createFID(workDir) }

        public companion object {

            /**
             * Opener for creating a [Environment] instance.
             *
             * If an [Environment] has already been created for the provided
             * [workDir], that [Environment] instance will be returned.
             *
             * [workDir] should be specified within your application's home
             * directory (e.g. `$HOME/.my_application/torservice`)
             *
             * [cacheDir] should be specified within your application's cache
             * directory (e.g. `$HOME/.my_application/cache/torservice`)
             *
             * It is advisable to keep the dirname for [workDir] and [cacheDir]
             * identical (e.g. `torservice`), especially when creating multiple
             * instances of [Environment].
             *
             * When running multiple instances, declaring the same [cacheDir] as
             * another [Environment] will result in a bad day. No checks are
             * performed for this clash.
             *
             * @param [workDir] tor's working directory (e.g. `$HOME/.my_application/torservice`)
             * @param [cacheDir] tor's cache directory (e.g. `$HOME/.my_application/cache/torservice`)
             * @param [installer] lambda for creating [ResourceInstaller] using
             *   the default [Builder.installationDir]
             * @see [io.matthewnelson.kmp.tor.runtime.mobile.createTorRuntimeEnvironment]
             * */
            @JvmStatic
            public fun Builder(
                workDir: File,
                cacheDir: File,
                installer: (installationDir: File) -> ResourceInstaller<Paths.Tor>,
            ): Environment = Builder.build(workDir, cacheDir, installer, null)

            /**
             * Opener for creating a [Environment] instance.
             *
             * If an [Environment] has already been created for the provided
             * [workDir], that [Environment] instance will be returned.
             *
             * [workDir] should be specified within your application's home
             * directory (e.g. `$HOME/.my_application/torservice`)
             *
             * [cacheDir] should be specified within your application's cache
             * directory (e.g. `$HOME/.my_application/cache/torservice`)
             *
             * It is advisable to keep the dirname for [workDir] and [cacheDir]
             * identical (e.g. `torservice`), especially when creating multiple
             * instances of [Environment].
             *
             * When running multiple instances, declaring the same [cacheDir] as
             * another [Environment] will result in a bad day. No checks are
             * performed for this clash.
             *
             * @param [workDir] tor's working directory (e.g. `$HOME/.my_application/torservice`)
             * @param [cacheDir] tor's cache directory (e.g. `$HOME/.my_application/cache/torservice`)
             * @param [installer] lambda for creating [ResourceInstaller] using
             *   the default [Builder.installationDir]
             * @param [block] optional lambda for modifying default parameters.
             * @see [io.matthewnelson.kmp.tor.runtime.mobile.createTorRuntimeEnvironment]
             * */
            @JvmStatic
            public fun Builder(
                workDir: File,
                cacheDir: File,
                installer: (installationDir: File) -> ResourceInstaller<Paths.Tor>,
                block: ThisBlock<Builder>,
            ): Environment = Builder.build(workDir, cacheDir, installer, block)
        }

        @KmpTorDsl
        public class Builder private constructor(
            @JvmField
            public val workDir: File,
            @JvmField
            public val cacheDir: File,
        ) {

            /**
             * The directory for which **all** resources will be installed
             * */
            @JvmField
            public var installationDir: File = workDir

            @JvmField
            public var torrcFile: File = workDir.resolve("torrc")

            @JvmField
            public var torrcDefaultsFile: File = workDir.resolve("torrc-defaults")

            internal companion object: InstanceKeeper<File, Environment>() {

                @JvmSynthetic
                internal fun build(
                    workDir: File,
                    cacheDir: File,
                    installer: (installationDir: File) -> ResourceInstaller<Paths.Tor>,
                    block: ThisBlock<Builder>?,
                ): Environment {
                    val b = Builder(workDir.absoluteFile.normalize(), cacheDir.absoluteFile.normalize())
                    // Apply block outside getOrCreateInstance call to
                    // prevent double instance creation
                    if (block != null) b.apply(block)
                    val torResource = installer(b.installationDir.absoluteFile.normalize())

                    return getOrCreateInstance(key = b.workDir, block = {
                        Environment(
                            workDir = b.workDir,
                            cacheDir = b.cacheDir,
                            torrcFile = b.torrcFile.absoluteFile.normalize(),
                            torrcDefaultsFile = b.torrcDefaultsFile.absoluteFile.normalize(),
                            torResource = torResource,
                        )
                    })
                }
            }
        }

        @get:JvmSynthetic
        internal val staticTag: String by lazy {
            try {
                SecureRandom().nextBytesOf(16)
            } catch (_: SecRandomCopyException) {
                Random.Default.nextBytes(16)
            }.encodeToString(Base16)
        }

        public override fun equals(other: Any?): Boolean = other is Environment && other.fid == fid
        public override fun hashCode(): Int = 17 * 31 + fid.hashCode()
        public override fun toString(): String = toFIDString(includeHashCode = false)
    }

    @InternalKmpTorApi
    public interface ServiceFactory:
        TorEvent.Processor,
        RuntimeEvent.Processor,
        RuntimeEvent.Notifier,
        FileID
    {

        public fun environment(): Environment

        public fun newRuntime(
            serviceEvents: Set<TorEvent>,
            serviceObserver: NetworkObserver?,
        ): Lifecycle.DestroyableRuntime

        @InternalKmpTorApi
        public companion object {

            @JvmStatic
            @InternalKmpTorApi
            @Throws(IllegalStateException::class)
            public fun checkInstance(factory: ServiceFactory) { factory.checkInstance() }
        }
    }
}
