/*
 * Copyright (c) 2021 Matthew Nelson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
package io.matthewnelson.kmp.tor.manager.common.event

import io.matthewnelson.kmp.tor.common.address.Port
import io.matthewnelson.kmp.tor.controller.common.events.TorEvent
import io.matthewnelson.kmp.tor.manager.common.exceptions.TorManagerException
import io.matthewnelson.kmp.tor.manager.common.state.*
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmStatic
import kotlin.reflect.KClass

/**
 * TorManager events that are dispatched to attached [Listener]s
 *
 * @see [Listener]
 * @see [SealedListener]
 * */
sealed interface TorManagerEvent {

    /**
     * Current [Action] being processed by TorManager
     * */
    sealed class Action: TorManagerEvent {
        object Controller : Action() {
            override fun toString(): String = CONTROLLER
        }
        object Restart    : Action() {
            override fun toString(): String = RESTART
        }
        object Start      : Action() {
            override fun toString(): String = START
        }
        object Stop       : Action() {
            override fun toString(): String = STOP
        }

        companion object {
            private const val CONTROLLER = "Action.Controller"
            private const val RESTART = "Action.Restart"
            private const val START = "Action.Start"
            private const val STOP = "Action.Stop"
        }
    }

    sealed interface Log: TorManagerEvent {

        /**
         * Debug events. Will only be dispatched if debug is enabled.
         * */
        @JvmInline
        value class Debug(val value: String): Log {
            override fun toString(): String = value
        }

        /**
         * Error events that are not returned as a [Result] from interacting
         * with TorManager.
         * */
        @JvmInline
        value class Error(val value: Throwable): Log

        /**
         * Warning events. Currently, the only warning is [WAITING_ON_NETWORK].
         * */
        @JvmInline
        value class Warn(val value: String): Log {
            companion object {
                const val WAITING_ON_NETWORK = "No Network Connectivity. Waiting..."
            }
        }
    }

    /**
     * Lifecycle events are dispatched for various classes in order to notify
     * attached [Listener]s and trigger reactive events. This is to aid primarily
     * in the self destruction and clean up process of lifecycle-aware components.
     *
     * All [event] Strings that are dispatched can be found in [Companion] as constants.
     * */
    class Lifecycle<T: Any> private constructor(
        val clazz: KClass<T>,
        val hash: Int,
        val event: String
    ) : TorManagerEvent {

        override fun equals(other: Any?): Boolean {
            return  other != null               &&
                    other is Lifecycle<*>       &&
                    other.clazz == clazz        &&
                    other.hash == hash          &&
                    other.event == event
        }

        override fun hashCode(): Int {
            var result = 17
            result = result * 31 + clazz.hashCode()
            result = result * 31 + hash
            result = result * 31 + event.hashCode()
            return result
        }

        override fun toString(): String {
            return "Lifecycle(class=${clazz.simpleName ?: "Unknown"}@$hash, event=$event)"
        }

        companion object {
            const val ON_CREATE = "onCreate"
            const val ON_DESTROY = "onDestroy"

            // Android's TorService
            const val ON_BIND = "onBind"
            const val ON_START_COMMAND = "onStartCommand"
            const val ON_TASK_REMOVED = "onTaskRemoved"
            const val ON_TASK_RETURNED = "onTaskReturned"

            // Android Receivers
            const val ON_REGISTER = "onRegister"
            const val ON_UNREGISTER = "onUnregister"

            @JvmStatic
            operator fun invoke(any: Any, event: String): Lifecycle<*> =
                Lifecycle(any::class, any.hashCode(), event)
        }
    }

    /**
     * [AddressInfo] is dispatched upon Bootstrap completion. Subsequently, if a
     * port listener is opened or closed, a new [AddressInfo] is dispatched with
     * the updated information.
     *
     * Upon stopping of Tor, or setting DisableNetwork to true, a new [AddressInfo]
     * with all arguments set to `null` is dispatched.
     *
     * Example address: 127.0.0.1:48494
     * */
    data class AddressInfo(
        val dns: String? = null,
        val http: String? = null,
        val socks: String? = null,
        val trans: String? = null,
    ): TorManagerEvent {
        val isNull: Boolean = dns == null && http == null && socks == null && trans == null

        fun splitDns(): Result<Pair<String, Port>?> = split("DNS", dns)
        fun splitHttp(): Result<Pair<String, Port>?> = split("HTTP", http)
        fun splitSocks(): Result<Pair<String, Port>?> = split("Socks", socks)
        fun splitTrans(): Result<Pair<String, Port>?> = split("Trans", trans)

        private fun split(port: String, value: String?): Result<Pair<String, Port>> {
            if (value == null) {
                return Result.failure(TorManagerException("$port address was null"))
            }

            val splits = value.split(':')

            return try {
                Result.success(Pair(splits[0].trim(), Port(splits[1].trim().toInt())))
            } catch (e: Exception) {
                Result.failure(TorManagerException("Failed to split $port address: $value", e))
            }
        }
    }

    data class State(val torState: TorState, val networkState: TorNetworkState): TorManagerEvent {
        inline val isOff: Boolean get() = torState.isOff()
        inline val isOn: Boolean get() = torState.isOn()
        inline val isStarting: Boolean get() = torState.isStarting()
        inline val isStopping: Boolean get() = torState.isStopping()

        inline val isNetworkEnabled: Boolean get() = networkState.isEnabled()
        inline val isNetworkDisabled: Boolean get() = networkState.isDisabled()
    }

    /**
     * Prefer using [Listener] over [SealedListener] when possible, as future
     * additions to [TorManagerEvent] sealed classes can produce errors which would
     * otherwise be handled for you here with the addition of another open method
     * to override upon updating.
     * */
    interface SealedListener: TorEvent.SealedListener {
        fun onEvent(event: TorManagerEvent)
    }

    abstract class Listener: TorEvent.Listener(), SealedListener {
        open fun managerEventActionController() {}
        open fun managerEventActionRestart() {}
        open fun managerEventActionStart() {}
        open fun managerEventActionStop() {}
        open fun managerEventAddressInfo(info: AddressInfo) {}
        open fun managerEventDebug(message: String) {}
        open fun managerEventError(t: Throwable) {}
        open fun managerEventWarn(message: String) {}
        open fun managerEventLifecycle(lifecycle: Lifecycle<*>) {}
        open fun managerEventState(state: State) {}

        override fun onEvent(event: TorManagerEvent) {
            when (event) {
                is Action.Controller -> managerEventActionController()
                is Action.Restart -> managerEventActionRestart()
                is Action.Start -> managerEventActionStart()
                is Action.Stop -> managerEventActionStop()
                is AddressInfo -> managerEventAddressInfo(event)
                is Log.Debug -> managerEventDebug(event.value)
                is Log.Error -> managerEventError(event.value)
                is Log.Warn -> managerEventWarn(event.value)
                is Lifecycle<*> -> managerEventLifecycle(event)
                is State -> managerEventState(event)
            }
        }
    }
}
