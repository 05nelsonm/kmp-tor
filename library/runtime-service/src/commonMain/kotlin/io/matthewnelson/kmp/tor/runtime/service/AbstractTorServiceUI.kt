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
import io.matthewnelson.kmp.tor.runtime.TorRuntime
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
 * @see [io.matthewnelson.kmp.tor.runtime.service.TorServiceUI]
 * @throws [IllegalStateException] on instantiation if [args] were not those
 *   which were passed to [Factory.newInstance]. See [Args].
 * */
public abstract class AbstractTorServiceUI<A: AbstractTorServiceUI.Args, C: AbstractTorServiceUI.Config>
@Throws(IllegalStateException::class)
internal constructor(
    private val args: A,
    init: Any,
) {

    init {
        args.initialize()
        check(init == INIT) { "AbstractTorServiceUI cannot be extended" }
    }

    private val serviceJob = args.serviceScope().coroutineContext.job

    /**
     * The default [Config] that was defined for [Factory.defaultConfig]
     * */
    @JvmField
    protected val defaultConfig: C = args.defaultConfig()

    /**
     * A [CoroutineScope] which is a child of the service object's
     * [CoroutineScope].
     * */
    @JvmField
    protected val serviceChildScope: CoroutineScope = CoroutineScope(context =
        args.serviceScope().coroutineContext
        + CoroutineName("TorServiceUIScope@${args.hashCode()}")
        + SupervisorJob(serviceJob)
    )

    protected fun isDestroyed(): Boolean = !serviceJob.isActive

    protected open fun onDestroy() {}

    /**
     * Core `commonMain` abstraction for passing platform specific arguments
     * in an encapsulated manner when instantiating new instances of
     * [AbstractTorServiceUI] implementations.
     *
     * [Args] are single use items and must be consumed only once, otherwise
     * an exception is raised when [Factory.newInstance] is called resulting
     * a service start failure.
     *
     * @see [io.matthewnelson.kmp.tor.runtime.service.TorServiceUI.Args]
     * */
    public abstract class Args internal constructor(
        private val _defaultConfig: Config,
        private val _serviceScope: CoroutineScope,
        init: Any,
    ) {

        init {
            check(init == INIT) { "AbstractTorServiceUI.Args cannot be extended" }
        }

        @Volatile
        private var _isInitialized: Boolean = false

        @OptIn(InternalKmpTorApi::class)
        private val lock = SynchronizedObject()

        @JvmSynthetic
        @Suppress("UNCHECKED_CAST")
        internal fun <C: Config> defaultConfig(): C = _defaultConfig as C
        @JvmSynthetic
        internal fun serviceScope(): CoroutineScope = _serviceScope

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
    public abstract class Factory<A: Args, C: Config, UI: AbstractTorServiceUI<A, C>> internal constructor(

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
         * [newInstance]. See [Args].
         * */
        protected abstract fun newInstanceProtected(args: A): UI

        @JvmSynthetic
        @Throws(IllegalStateException::class)
        internal fun newInstance(args: A): UI {
            val i = newInstanceProtected(args)

            try {
                // Should already be initialized from instance init
                // block and thus should throw exception here.
                args.initialize()
            } catch (_: IllegalStateException) {
                // args are initialized. Ensure args
                check(i.args == args) { "$args were not used to instantiate instance $i" }

                // Set completion handler to call onDestroy.
                i.serviceJob.invokeOnCompletion { i.onDestroy() }
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
            internal fun <C: Config> Config.unsafeCastAsType(other: C): C {
                val otherClazz = this::class
                val thisClazz = other::class

                require(thisClazz == otherClazz) {
                    "this[$thisClazz] is not the same type as other[$otherClazz]"
                }

                @Suppress("UNCHECKED_CAST")
                return this as C
            }
        }
    }

    public final override fun equals(other: Any?): Boolean {
        if (other !is AbstractTorServiceUI<*, *>) return false
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
