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
package io.matthewnelson.kmp.tor.runtime.service

import io.matthewnelson.immutable.collections.toImmutableMap
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.resource.SynchronizedObject
import io.matthewnelson.kmp.tor.core.resource.synchronized
import io.matthewnelson.kmp.tor.runtime.Lifecycle
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent
import io.matthewnelson.kmp.tor.runtime.TorRuntime
import io.matthewnelson.kmp.tor.runtime.core.TorEvent
import io.matthewnelson.kmp.tor.runtime.service.AbstractTorServiceUI.Factory.Companion.unsafeCastAsType
import kotlinx.coroutines.*
import kotlin.concurrent.Volatile
import kotlin.jvm.JvmField
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
) {

    init {
        args.initialize()
        check(init == INIT) { "AbstractTorServiceUI cannot be extended" }
    }

    private val serviceJob = args.scope().coroutineContext.job

    /**
     * The default [Config] that was defined for [Factory.defaultConfig]
     * */
    @JvmField
    protected val defaultConfig: C = args.config()

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
     * @see [io.matthewnelson.kmp.tor.runtime.service.TorServiceUI.Config]
     * @throws [IllegalArgumentException] if [fields] is empty
     * */
    public abstract class Config
    @Throws(IllegalArgumentException::class)
    internal constructor(
        fields: Map<String, Any>,
        init: Any,
    ) {

        init {
            check(init == INIT) { "AbstractTorServiceUI.Config cannot be extended" }
        }

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
                append(value)
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
     * */
    public abstract class InstanceState<C: Config>
    @Throws(IllegalStateException::class)
    protected constructor(private val args: Args.Instance) {

        init {
            args.initialize()
        }

        private val instanceJob: Job = args.scope().coroutineContext.job

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

        init {
            instanceJob.invokeOnCompletion { onDestroy() }
        }

        public final override fun equals(other: Any?): Boolean {
            if (other !is InstanceState<*>) return false
            if (other::class != this::class) return false
            return other.instanceJob == instanceJob
        }

        public final override fun hashCode(): Int = instanceJob.hashCode()
    }

    protected abstract fun newInstanceStateProtected(args: Args.Instance): IS

    @JvmSynthetic
    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    internal fun newInstanceState(
        instanceConfig: Config?,
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

        val args = InstanceArgs(config, instanceScope)
        val i = newInstanceStateProtected(args)

        try {
            args.initialize()
        } catch (_: IllegalStateException) {
            // InstanceState.hashCode() is overridden to return instanceJob.hashCode()
            check(i.hashCode() == instanceJob.hashCode()) {
                instanceJob.cancel()

                "$args were not used to instantiate instance $i"
            }

            return instanceJob to i
        }

        // args did not throw exception and were just initialized in this
        // function body meaning newInstanceStateProtected implementation held
        // onto them and returned a different instance of InstanceState
        instanceJob.cancel()
        throw IllegalStateException("$args were not used to create $i")
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

    private inner class InstanceArgs(
        instanceConfig: Config,
        instanceScope: CoroutineScope,
    ): Args.Instance(instanceConfig, instanceScope)
}
