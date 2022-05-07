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
import io.matthewnelson.kmp.tor.common.address.ProxyAddress
import io.matthewnelson.kmp.tor.common.annotation.ExperimentalTorApi
import io.matthewnelson.kmp.tor.common.annotation.SealedValueClass
import io.matthewnelson.kmp.tor.controller.common.events.TorEvent
import io.matthewnelson.kmp.tor.manager.common.exceptions.TorManagerException
import io.matthewnelson.kmp.tor.manager.common.state.*
import kotlin.jvm.JvmField
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmStatic
import kotlin.reflect.KClass

/**
 * TorManager events that are dispatched to attached [Listener]s
 *
 * @see [Listener]
 * @see [SealedListener]
 * */
@Suppress("DEPRECATION")
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
        @SealedValueClass
        @OptIn(ExperimentalTorApi::class)
        sealed interface Debug: Log {
            val value: String

            companion object {
                @JvmStatic
                operator fun invoke(value: String): Debug {
                    return RealDebug(value)
                }
            }
        }

        @JvmInline
        private value class RealDebug(override val value: String): Debug {
            override fun toString(): String = "D/$value"
        }

        /**
         * Error events that are not returned as a [Result] from interacting
         * with TorManager.
         * */
        @SealedValueClass
        @OptIn(ExperimentalTorApi::class)
        sealed interface Error: Log {
            val value: Throwable

            companion object {
                @JvmStatic
                operator fun invoke(value: Throwable): Error {
                    return RealError(value)
                }
            }
        }

        @JvmInline
        private value class RealError(override val value: Throwable): Error {
            override fun toString(): String = "E/$value"
        }

        @SealedValueClass
        @OptIn(ExperimentalTorApi::class)
        sealed interface Info: Log {
            val value: String

            companion object {
                @JvmStatic
                operator fun invoke(value: String): Info {
                    return RealInfo(value)
                }
            }
        }

        @JvmInline
        private value class RealInfo(override val value: String): Info {
            override fun toString(): String = "I/$value"
        }

        /**
         * Warning events. Currently, the only warning is [WAITING_ON_NETWORK].
         * */
        @SealedValueClass
        @OptIn(ExperimentalTorApi::class)
        sealed interface Warn: Log {
            val value: String

            companion object {
                const val WAITING_ON_NETWORK = "No Network Connectivity. Waiting..."

                @JvmStatic
                operator fun invoke(value: String): Warn {
                    return RealWarn(value)
                }
            }
        }

        @JvmInline
        private value class RealWarn(override val value: String): Warn {
            override fun toString(): String = "W/$value"
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
        @JvmField
        val clazz: KClass<T>,
        @JvmField
        val hash: Int,
        @JvmField
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
     * [AddressInfo] is dispatched upon Bootstrap completion (when
     * [TorState.On.isBootstrapped] is `true`). Subsequently, if a
     * port listener is opened or closed, a new [AddressInfo] is
     * dispatched with the updated address information.
     *
     * Upon stopping of Tor, or setting DisableNetwork to true, a
     * new [AddressInfo] with all arguments set to `null` is dispatched.
     *
     * Addresses dispatched from TorManager will either be `null`, or
     * contain at least 1 address. You will never encounter [EmptySet].
     *
     * Example address: 127.0.0.1:48494
     * */
    data class AddressInfo(
        @JvmField
        val dns: Set<String>? = null,
        @JvmField
        val http: Set<String>? = null,
        @JvmField
        val socks: Set<String>? = null,
        @JvmField
        val trans: Set<String>? = null,
    ): TorManagerEvent {
        val isNull: Boolean = dns == null && http == null && socks == null && trans == null

        /**
         * Transform [dns] proxy addresses into individual [ProxyAddress]'s.
         *
         * @throws [IllegalStateException] if [dns] info is null.
         * @throws [TorManagerException] if any addresses failed to translate.
         * */
        @Throws(IllegalStateException::class, TorManagerException::class)
        fun dnsInfoToProxyAddress(): Set<ProxyAddress> = split(dns, "AddressInfo.dns")
        fun dnsInfoToProxyAddressOrNull(): Set<ProxyAddress>? {
            return try {
                dnsInfoToProxyAddress()
            } catch (_: RuntimeException) {
                null
            }
        }

        /**
         * Transform [http] proxy addresses into individual [ProxyAddress]'s.
         *
         * @throws [IllegalStateException] if [http] info is null.
         * @throws [TorManagerException] if any addresses failed to translate.
         * */
        @Throws(IllegalStateException::class, TorManagerException::class)
        fun httpInfoToProxyAddress(): Set<ProxyAddress> = split(http, "AddressInfo.http")
        fun httpInfoToProxyAddressOrNull(): Set<ProxyAddress>? {
            return try {
                httpInfoToProxyAddress()
            } catch (_: RuntimeException) {
                null
            }
        }

        /**
         * Transform [socks] proxy addresses into individual [ProxyAddress]'s.
         *
         * @throws [IllegalStateException] if [socks] info is null.
         * @throws [TorManagerException] if any addresses failed to translate.
         * */
        @Throws(IllegalStateException::class, TorManagerException::class)
        fun socksInfoToProxyAddress(): Set<ProxyAddress> = split(socks, "AddressInfo.socks")
        fun socksInfoToProxyAddressOrNull(): Set<ProxyAddress>? {
            return try {
                socksInfoToProxyAddress()
            } catch (_: RuntimeException) {
                null
            }
        }

        /**
         * Transform [trans] proxy addresses into individual [ProxyAddress]'s.
         *
         * @throws [IllegalStateException] if [trans] info is null.
         * @throws [TorManagerException] if any addresses failed to translate.
         * */
        @Throws(IllegalStateException::class, TorManagerException::class)
        fun transInfoToProxyAddress(): Set<ProxyAddress> = split(trans, "AddressInfo.trans")
        fun transInfoToProxyAddressOrNull(): Set<ProxyAddress>? {
            return try {
                transInfoToProxyAddress()
            } catch (_: RuntimeException) {
                null
            }
        }

        @Throws(IllegalStateException::class, TorManagerException::class)
        private fun split(values: Set<String>?, fieldName: String): Set<ProxyAddress> {
            if (values.isNullOrEmpty()) {
                throw IllegalStateException("$fieldName contained no info")
            }

            val set: MutableSet<ProxyAddress> = LinkedHashSet(values.size)
            for (value in values) {
                try {
                    set.add(ProxyAddress.fromString(value))
                } catch (e: IllegalArgumentException) {
                    throw TorManagerException("Failed to parse $fieldName address: $value", e)
                }
            }

            return set.toSet()
        }

        @Deprecated(
            message = "Moved to kmp-tor-common module as ProxyAddress",
            replaceWith = ReplaceWith("io.matthewnelson.kmp.tor.common.address.ProxyAddress"),
            level = DeprecationLevel.WARNING
        )
        data class Address(
            @JvmField
            val address: String,
            @JvmField
            val port: Port
        ) {
            companion object {
                @Throws(
                    IllegalArgumentException::class,
                    IndexOutOfBoundsException::class,
                    NumberFormatException::class
                )
                @JvmStatic
                fun fromString(value: String): Address {
                    val splits = value.split(':')
                    return Address(
                        splits[0].trim(),
                        Port(splits[1].trim().toInt())
                    )
                }
            }
        }

        @Deprecated(
            message = "Replaced with non-Result return type and new common module class",
            replaceWith = ReplaceWith("dnsInfoToProxyAddressOrNull()"),
            level = DeprecationLevel.WARNING
        )
        fun splitDns(): Result<Set<Address>> = split("DNS", dns)

        @Deprecated(
            message = "Replaced with non-Result return type and new common module class",
            replaceWith = ReplaceWith("httpInfoToProxyAddressOrNull()"),
            level = DeprecationLevel.WARNING
        )
        fun splitHttp(): Result<Set<Address>> = split("HTTP", http)

        @Deprecated(
            message = "Replaced with non-Result return type and new common module class",
            replaceWith = ReplaceWith("socksInfoToProxyAddressOrNull()"),
            level = DeprecationLevel.WARNING
        )
        fun splitSocks(): Result<Set<Address>> = split("Socks", socks)

        @Deprecated(
            message = "Replaced with non-Result return type and new common module class",
            replaceWith = ReplaceWith("transInfoToProxyAddressOrNull()"),
            level = DeprecationLevel.WARNING
        )
        fun splitTrans(): Result<Set<Address>> = split("Trans", trans)

        private fun split(portName: String, values: Set<String>?): Result<Set<Address>> {
            if (values.isNullOrEmpty()) {
                return Result.failure(TorManagerException("$portName address was null"))
            }

            val set: MutableSet<Address> = LinkedHashSet(values.size)
            for (value in values) {
                try {
                    set.add(Address.fromString(value))
                } catch (e: Exception) {
                    return Result.failure(
                        TorManagerException("Failed to split $portName address: $value", e)
                    )
                }
            }

            return Result.success(set.toSet())
        }

        companion object {
            @get:JvmStatic
            val NULL_VALUES = AddressInfo()
        }
    }

    /**
     * Will be dispatched a single time for a given Tor instance start operation
     * after bootstrapping has been completed. This can be relied on to perform
     * one time operations, such as adding hidden services and client auth keys.
     *
     * A new [StartUpCompleteForTorInstance] will be dispatched in the event Tor
     * is stopped, then started again and completes it's bootstrapping process.
     * */
    object StartUpCompleteForTorInstance: TorManagerEvent {
        override fun toString(): String {
            return "StartUpCompleteForTorInstance"
        }
    }

    data class State(
        @JvmField
        val torState: TorState,
        @JvmField
        val networkState: TorNetworkState
    ): TorManagerEvent {
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
        open fun managerEventInfo(message: String) {}
        open fun managerEventWarn(message: String) {}
        open fun managerEventLifecycle(lifecycle: Lifecycle<*>) {}
        open fun managerEventStartUpCompleteForTorInstance() {}
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
                is Log.Info -> managerEventInfo(event.value)
                is Log.Warn -> managerEventWarn(event.value)
                is Lifecycle<*> -> managerEventLifecycle(event)
                is StartUpCompleteForTorInstance -> managerEventStartUpCompleteForTorInstance()
                is State -> managerEventState(event)
            }
        }
    }
}
