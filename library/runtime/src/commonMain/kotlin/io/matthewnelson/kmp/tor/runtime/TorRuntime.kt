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
import io.matthewnelson.immutable.collections.toImmutableList
import io.matthewnelson.immutable.collections.toImmutableSet
import io.matthewnelson.kmp.file.*
import io.matthewnelson.kmp.tor.core.api.ResourceInstaller
import io.matthewnelson.kmp.tor.core.api.ResourceInstaller.Paths
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.api.annotation.KmpTorDsl
import io.matthewnelson.kmp.tor.runtime.ctrl.api.*
import io.matthewnelson.kmp.tor.runtime.internal.InstanceKeeper
import io.matthewnelson.kmp.tor.runtime.internal.RealTorRuntime
import io.matthewnelson.kmp.tor.runtime.internal.RealTorRuntime.Companion.checkInstance
import io.matthewnelson.kmp.tor.runtime.internal.sha256
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlin.jvm.JvmField
import kotlin.jvm.JvmName
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic
import kotlin.random.Random

public interface TorRuntime: TorEvent.Processor, RuntimeEvent.Processor {

    public fun environment(): Environment

    /**
     * Starts the tor daemon.
     *
     * If tor is running, will do nothing.
     * */
    public fun startDaemon()

    /**
     * Stops the tor daemon.
     *
     * If tor is not running, will do nothing.
     * */
    public fun stopDaemon()

    /**
     * Stops and then starts the tor daemon.
     *
     * If tor is not running, will do nothing.
     * */
    public fun restartDaemon()

    public companion object {

        /**
         * Opener for creating a new [TorRuntime] instance.
         *
         * @see [Environment]
         * @see [TorRuntime.Builder]
         * */
        @JvmStatic
        public fun Builder(
            environment: Environment,
            block: ThisBlock<Builder>,
        ): TorRuntime = Builder.build(environment, block)
    }

    // do not annotate with KmpTorDsl so that environment is
    // available within config lambda
    public class Builder private constructor(
        @JvmField
        public val environment: Environment,
    ) {

        private var config = mutableListOf<ThisBlock<TorConfig.Builder>>()
        private val staticTorEvents = mutableSetOf(TorEvent.CONF_CHANGED, TorEvent.NOTICE)
        private val staticTorEventObservers = mutableSetOf<TorEvent.Observer>()
        private val staticRuntimeEventObservers = mutableSetOf<RuntimeEvent.Observer<*>>()

        /**
         * In the event that a configured TCP port is unavailable on the host
         * device, tor will fail to startup.
         *
         * Setting this to true will result in reassignment of any unavailable
         * TCP port arguments to "auto" just prior to startup in order to
         * mitigate start failures.
         * */
        @JvmField
        public var allowPortReassignment: Boolean = true

        @JvmField
        public var networkObserver: NetworkObserver = NetworkObserver.NOOP

        /**
         * If false, will use [Dispatchers.Main] when dispatching events.
         * */
        @JvmField
        public var eventThreadBackground: Boolean = true

        /**
         * Configure the [TorConfig] at each startup. Multiple [block] may
         * be set, each of which will be applied to the [TorConfig.Builder]
         * before starting tor.
         *
         * [block] is always invoked from a background thread, so it is safe
         * to perform IO within the lambda (e.g. writing settings that are
         * not currently supported to the torrc file).
         *
         * Any exceptions thrown are propagated to the caller of start.
         *
         * **NOTE:** You can safely reference [environment] from within
         * the lambda to use when creating [TorConfig].
         * */
        @KmpTorDsl
        public fun config(
            block: ThisBlock<TorConfig.Builder>,
        ): Builder {
            config.add(block)
            return this
        }

        /**
         * Add [TorEvent] that are required for your implementation. All
         * configured [staticEvent] will be set at startup when the control
         * connection is established via SETEVENTS.
         *
         * Any subsequent calls for SETEVENTS during runtime will be intercepted
         * and modified to include all configured [staticEvent].
         * */
        @KmpTorDsl
        public fun staticEvent(
            event: TorEvent,
        ): Builder {
            staticTorEvents.add(event)
            return this
        }

        /**
         * Add [TorEvent.Observer] which will never be removed from [TorRuntime].
         * Useful for logging purposes.
         * */
        @KmpTorDsl
        public fun staticObserver(
            event: TorEvent,
            block: ItBlock<String>,
        ): Builder {
            val observer = event.observer(environment.staticObserverTag, block)
            staticTorEventObservers.add(observer)
            return this
        }

        /**
         * Add [RuntimeEvent.Observer] which will never be removed from [TorRuntime].
         * Useful for logging purposes.
         * */
        @KmpTorDsl
        public fun <R: Any> staticObserver(
            event: RuntimeEvent<R>,
            block: ItBlock<R>,
        ): Builder {
            val observer = event.observer(environment.staticObserverTag, block)
            staticRuntimeEventObservers.add(observer)
            return this
        }

        internal companion object: InstanceKeeper<Environment, TorRuntime>() {

            @JvmSynthetic
            internal fun build(
                environment: Environment,
                block: ThisBlock<Builder>?,
            ): TorRuntime = getOrCreateInstance(environment) {
                val b = Builder(environment)
                if (block != null) b.apply(block)

                RealTorRuntime.of(
                    environment = environment,
                    networkObserver = b.networkObserver,
                    allowPortReassignment = b.allowPortReassignment,
                    eventThreadBackground = b.eventThreadBackground,
                    config = b.config.toImmutableList(),
                    staticTorEvents = b.staticTorEvents.toImmutableSet(),
                    staticTorEventObservers = b.staticTorEventObservers.toImmutableSet(),
                    staticRuntimeEventObservers = b.staticRuntimeEventObservers.toImmutableSet(),
                )
            }
        }
    }

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
    ) {

        @get:JvmName("id")
        public val id: String by lazy { workDir.path.encodeToByteArray().sha256() }

        // TODO: debug & ability for RealTorRuntime to attach
        // TODO: hashPassword

        public companion object {

            @JvmStatic
            public fun Builder(
                workDir: File,
                cacheDir: File,
                installer: (installationDir: File) -> ResourceInstaller<Paths.Tor>,
            ): Environment = Builder.build(workDir, cacheDir, installer, null)

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
             * The directory in which **all** resources will be installed
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
                    block: ThisBlock<Builder>?
                ): Environment {
                    val absoluteWorkDir = workDir.absoluteFile.normalize()

                    return getOrCreateInstance(key = absoluteWorkDir) {
                        val b = Builder(absoluteWorkDir, cacheDir.absoluteFile.normalize())
                        if (block != null) b.apply(block)

                        val torResource = installer(b.installationDir.absoluteFile.normalize())

                        Environment(
                            workDir = b.workDir,
                            cacheDir = b.cacheDir,
                            torrcFile = b.torrcFile.absoluteFile.normalize(),
                            torrcDefaultsFile = b.torrcDefaultsFile.absoluteFile.normalize(),
                            torResource = torResource,
                        )
                    }
                }
            }
        }

        @get:JvmSynthetic
        internal val staticObserverTag: String by lazy {
            Random.Default.nextBytes(16).encodeToString(Base16)
        }
    }

    @InternalKmpTorApi
    public interface ServiceFactory: TorEvent.Processor, RuntimeEvent.Processor {

        public val environment: Environment

        public fun <R: Any> RuntimeEvent<R>.notify(output: R)

        public fun create(
            lifecycleHook: Job,
            observer: NetworkObserver?,
        ): TorRuntime

        public companion object {

            @JvmStatic
            @InternalKmpTorApi
            @Throws(IllegalStateException::class)
            public fun checkInstance(factory: ServiceFactory) { factory.checkInstance() }
        }
    }
}
