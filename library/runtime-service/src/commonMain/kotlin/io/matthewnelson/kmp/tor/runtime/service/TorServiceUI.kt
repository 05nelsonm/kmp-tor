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
import io.matthewnelson.immutable.collections.toImmutableSet
import io.matthewnelson.kmp.tor.core.api.annotation.ExperimentalKmpTorApi
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.resource.SynchronizedObject
import io.matthewnelson.kmp.tor.core.resource.synchronized
import io.matthewnelson.kmp.tor.runtime.TorRuntime
import kotlinx.coroutines.Job
import kotlin.concurrent.Volatile
import kotlin.jvm.JvmField
import kotlin.jvm.JvmSynthetic

/**
 * Core abstraction for creating hooks to manage the UI state of
 * a service object running multiple instances of [TorRuntime].
 *
 * @see [io.matthewnelson.kmp.tor.runtime.service.AndroidTorServiceUI]
 * */
public abstract class TorServiceUI<A: TorServiceUI.Args, C: TorServiceUI.Config>
@ExperimentalKmpTorApi
@Throws(IllegalStateException::class)
internal constructor(
    private val args: A,
    init: Any,
) {

    @JvmField
    protected val defaultConfig: C = args.defaultConfig()

    protected fun isDestroyed(): Boolean = !args.serviceJob().isActive

    protected open fun onDestroy() {}

    init {
        args.serviceJob().invokeOnCompletion { onDestroy() }
    }

    /**
     * Core abstraction for passing platform specific arguments
     * in an encapsulated manner when instantiating new instances
     * of [TorServiceUI]. [Args] are single use items and must be
     * consumed, otherwise exceptions are raised and a probable crash
     * will ensue.
     * */
    public abstract class Args internal constructor(
        private val _defaultConfig: Config,
        private val _serviceJob: Job,
        init: Any,
    ) {

        @Volatile
        private var _isInitialized: Boolean = false

        @OptIn(InternalKmpTorApi::class)
        private val lock = SynchronizedObject()

        @JvmSynthetic
        @Suppress("UNCHECKED_CAST")
        internal fun <C: Config> defaultConfig(): C = _defaultConfig as C
        @JvmSynthetic
        internal fun serviceJob(): Job = _serviceJob

        @JvmSynthetic
        @Throws(IllegalStateException::class)
        internal fun initialize() {
            @OptIn(InternalKmpTorApi::class)
            val initialized = if (_isInitialized) {
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

            check(!initialized) {
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

        init {
            check(init == INIT) { "TorServiceUI.Args cannot be extended" }
        }
    }

    /**
     * Core abstraction for a [TorServiceUI] configuration.
     *
     * @throws [IllegalArgumentException] if [fields] is empty
     * */
    public abstract class Config internal constructor(
        fields: Map<String, Any>,
        init: Any,
    ) {

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

        init {
            check(init == INIT) { "TorServiceUI.Config cannot be extended" }
        }
    }

    /**
     * Core abstraction for creating new instances of [TorServiceUI].
     *
     * Implementations are encouraged to keep it as a subclass within,
     * and use a `private constructor` for, their [UI] implementations.
     *
     * As an example, see [io.matthewnelson.kmp.tor.runtime.service.ui.KmpTorServiceUI.Factory]
     * */
    public abstract class Factory<A: Args, C: Config, UI: TorServiceUI<A, C>>(

        /**
         * The default [Config] to use if one was not specified when
         * [TorRuntime.Environment] was instantiated.
         * */
        @JvmField
        public val defaultConfig: C,
        init: Any
    ) {

        /**
         * Implementors **MUST** utilize [args] to instantiate a new instance
         * of [UI]. If [args] were not consumed by the returned instance of
         * [UI], an exception will be thrown.
         * */
        protected abstract fun newInstanceProtected(args: A): UI

        @Throws(IllegalStateException::class)
        public fun newInstance(args: A): UI {
            val i = newInstanceProtected(args)

            try {
                // Should already be initialized from instance init
                // block and thus should throw exception.
                args.initialize()
            } catch (_: IllegalStateException) {
                // args are initialized. Ensure args
                check(i.args == args) {
                    "args@${args.hashCode()} were not used to instantiate instance of $i"
                }
                return i
            }

            // args did not throw exception. They were used for another
            // instance and an old instance was returned via Factory.newInstance
            throw IllegalStateException("args@${hashCode()} were not used to create $i")
        }

        init {
            check(init == INIT) { "TorServiceUI.Factory cannot be extended" }
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

    protected companion object {

        // Synthetic access to inhibit extension of abstract
        // classes externally. Only accessible within the
        // TorServiceUI class, and within the `runtime-service`
        // module.
        @JvmSynthetic
        internal val INIT = Any()
    }

    init {
        check(init == INIT) { "TorServiceUI cannot be extended" }
        args.initialize()
    }
}
