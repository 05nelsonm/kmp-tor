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
package io.matthewnelson.kmp.tor.runtime.core.ctrl

import io.matthewnelson.encoding.base16.Base16
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import io.matthewnelson.immutable.collections.immutableSetOf
import io.matthewnelson.immutable.collections.toImmutableSet
import io.matthewnelson.kmp.file.InterruptedException
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.core.*
import io.matthewnelson.kmp.tor.runtime.core.net.IPAddress
import io.matthewnelson.kmp.tor.runtime.core.net.OnionAddress
import io.matthewnelson.kmp.tor.runtime.core.ctrl.builder.BuilderScopeOnionAdd
import io.matthewnelson.kmp.tor.runtime.core.ctrl.builder.BuilderScopeOnionAdd.Companion.configure
import io.matthewnelson.kmp.tor.runtime.core.ctrl.builder.BuilderScopeClientAuthAdd
import io.matthewnelson.kmp.tor.runtime.core.config.TorConfig
import io.matthewnelson.kmp.tor.runtime.core.config.TorConfig.Companion.toConfig
import io.matthewnelson.kmp.tor.runtime.core.config.TorOption
import io.matthewnelson.kmp.tor.runtime.core.config.TorSetting
import io.matthewnelson.kmp.tor.runtime.core.key.*
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * Commands to interact with tor via its control connection, as described
 * in [control-spec](https://spec.torproject.org/control-spec/index.html).
 *
 * Commands are separated into 2 categories, [Privileged] and [Unprivileged].
 * This is unique to `kmp-tor` only, not tor. The purpose is to allow
 * abstractions to be built on top of a control connection implementation
 * (such as `kmp-tor:runtime`) that manage the connection and allow for
 * pass-through of [Unprivileged] commands while internally using [Privileged]
 * commands to set up and maintain the connection.
 *
 * @see [Privileged.Processor]
 * @see [Unprivileged.Processor]
 * */
@OptIn(InternalKmpTorApi::class)
public sealed class TorCmd<Success: Any> private constructor(
    @JvmField
    public val keyword: String,
): EnqueuedJob.Argument {

    /**
     * [control-spec#AUTHENTICATE](https://spec.torproject.org/control-spec/commands.html#authenticate)
     * */
    public class Authenticate: Privileged<Reply.Success.OK> {

        @JvmField
        public val hex: String

        /** No Password (i.e. An unauthenticated control connection) */
        public constructor(): this(NO_PASS)

        /** Un-Hashed (raw) Password for [TorOption.HashedControlPassword] (e.g. `"Hello World!"`) */
        public constructor(password: String): this(password.encodeToByteArray())

        /** Cookie authentication bytes (or password UTF-8 encoded to bytes) */
        public constructor(cookie: ByteArray): super("AUTHENTICATE") {
            hex = if (cookie.isEmpty()) "" else cookie.encodeToString(Base16())
        }

        private companion object {
            private val NO_PASS = ByteArray(0)
        }
    }

//    /**
//     * [control-spec#AUTHCHALLENGE](https://spec.torproject.org/control-spec/commands.html#authchallenge)
//     * */
//    public class ChallengeAuth: Privileged<Reply.Success.OK>("AUTHCHALLENGE")

//    public data object Circuit {
//
//        /**
//         * [control-spec#CLOSECIRCUIT](https://spec.torproject.org/control-spec/commands.html#closecircuit)
//         * */
//        public class Close: Unprivileged<Reply.Success.OK>("CLOSECIRCUIT")
//
//        /**
//         * [control-spec#EXTENDCIRCUIT](https://spec.torproject.org/control-spec/commands.html#extendcircuit)
//         * */
//        public class Extend: Unprivileged<String>("EXTENDCIRCUIT")
//
//        /**
//         * [control-spec#SETCIRCUITPURPOSE](https://spec.torproject.org/control-spec/commands.html#setcircuitpurpose)
//         * */
//        public class SetPurpose: Unprivileged<Reply.Success.OK>("SETCIRCUITPURPOSE")
//    }

    public data object Config {

        /**
         * [control-spec#GETCONF](https://spec.torproject.org/control-spec/commands.html#getconf)
         * */
        public class Get: Unprivileged<List<ConfigEntry>> {

            @JvmField
            public val options: kotlin.collections.Set<TorOption>

            public constructor(options: TorOption): this(immutableSetOf(options))
            public constructor(vararg options: TorOption): this(immutableSetOf(*options))
            public constructor(options: Collection<TorOption>): super("GETCONF") {
                this.options = options.toImmutableSet()
            }
        }

        /**
         * [control-spec#LOADCONF](https://spec.torproject.org/control-spec/commands.html#loadconf)
         * */
        public class Load: Privileged<Reply.Success.OK> {

            @JvmField
            public val config: TorConfig

            public constructor(block: ThisBlock<TorConfig.BuilderScope>): this(TorConfig.Builder(block))
            public constructor(config: TorConfig): super("LOADCONF") {
                this.config = config
            }
        }

        /**
         * [control-spec#RESETCONF](https://spec.torproject.org/control-spec/commands.html#resetconf)
         * */
        public class Reset: Unprivileged<Reply.Success.OK> {

            @JvmField
            public val options: kotlin.collections.Set<TorOption>

            public constructor(option: TorOption): this(immutableSetOf(option))
            public constructor(vararg options: TorOption): this(immutableSetOf(*options))
            public constructor(options: Collection<TorOption>): super("RESETCONF") {
                this.options = options.toImmutableSet()
            }
        }

        /**
         * [control-spec#SAVECONF](https://spec.torproject.org/control-spec/commands.html#saveconf)
         * */
        public class Save: Privileged<Reply.Success.OK> {

            @JvmField
            public val force: Boolean

            /** Default of [force] = `false` */
            public constructor(): this(force = false)
            public constructor(force: Boolean): super("SAVECONF") {
                this.force = force
            }
        }

        /**
         * [control-spec#SETCONF](https://spec.torproject.org/control-spec/commands.html#setconf)
         * */
        public class Set: Unprivileged<Reply.Success.OK> {

            @JvmField
            public val config: TorConfig

            public constructor(setting: TorSetting): this(setting.toConfig())
            public constructor(vararg settings: TorSetting): this(ThisBlock { settings.forEach { put(it) } })
            public constructor(settings: Collection<TorSetting>): this(ThisBlock { putAll(settings) })

            public constructor(block: ThisBlock<TorConfig.BuilderScope>): this(TorConfig.Builder(block))
            public constructor(config: TorConfig): super("SETCONF") {
                this.config = config
            }
        }
    }

    /**
     * [control-spec#DROPGUARDS](https://spec.torproject.org/control-spec/commands.html#dropguards)
     * */
    public data object DropGuards: Unprivileged<Reply.Success.OK>("DROPGUARDS")

    public data object Hs {

        /**
         * [control-spec#HSFETCH](https://spec.torproject.org/control-spec/commands.html#hsfetch)
         * */
        public class Fetch: Unprivileged<Reply.Success.OK> {

            @JvmField
            public val address: OnionAddress
            @JvmField
            public val servers: Set<String>

            public constructor(
                key: AddressKey.Public,
            ): this(key.address())

            public constructor(
                key: AddressKey.Public,
                server: String,
            ): this(key.address(), server)

            public constructor(
                key: AddressKey.Public,
                vararg servers: String,
            ): this(key, immutableSetOf(*servers))

            public constructor(
                key: AddressKey.Public,
                servers: Collection<String>,
            ): this(key.address(), servers)

            public constructor(
                address: OnionAddress,
            ): this(address, emptySet())

            public constructor(
                address: OnionAddress,
                server: String,
            ): this(address, immutableSetOf(server))

            public constructor(
                address: OnionAddress,
                vararg servers: String,
            ): this(address, immutableSetOf(*servers))

            public constructor(
                address: OnionAddress,
                servers: Collection<String>,
            ): super("HSFETCH") {
                this.address = address
                this.servers = servers.toImmutableSet()
            }
        }

//        /**
//         * [control-spec#HSPOST](https://spec.torproject.org/control-spec/commands.html#hspost)
//         * */
//        public class Post: Unprivileged<Reply.Success.OK>("HSPOST")
    }

    public data object Info {

        /**
         * [control-spec#GETINFO](https://spec.torproject.org/control-spec/commands.html#getinfo)
         * */
        public class Get: Unprivileged<Map<String, String>> {

            @JvmField
            public val keywords: Set<String>

            public constructor(keyword: String): this(immutableSetOf(keyword))
            public constructor(vararg keywords: String): this(immutableSetOf(*keywords))
            public constructor(keywords: Collection<String>): super("GETINFO") {
                this.keywords = keywords.toImmutableSet()
            }
        }

//        /**
//         * [control-spec#PROTOCOLINFO](https://spec.torproject.org/control-spec/commands.html#protocolinfo)
//         * */
//        public class Protocol: Privileged<Map<String, String>>("PROTOCOLINFO")
    }

    /**
     * [control-spec#MAPADDRESS](https://spec.torproject.org/control-spec/commands.html#mapaddress)
     *
     * @see [AddressMapping]
     * */
    public class MapAddress: Unprivileged<Set<AddressMapping.Result>> {

        @JvmField
        public val mappings: Set<AddressMapping>

        public constructor(mapping: AddressMapping): this(immutableSetOf(mapping))
        public constructor(vararg mappings: AddressMapping): this(immutableSetOf(*mappings))
        public constructor(mappings: Collection<AddressMapping>): super("MAPADDRESS") {
            this.mappings = mappings.toImmutableSet()
        }
    }

    public data object Onion {

        /**
         * [control-spec#ADD_ONION](https://spec.torproject.org/control-spec/commands.html#add_onion)
         *
         * @see [new]
         * @see [existing]
         * */
        public class Add: Unprivileged<HiddenServiceEntry> {

            @JvmField
            public val keyType: KeyType.Address<*, *>
            @JvmField
            public val key: AddressKey.Private?
            @JvmField
            public val clientAuth: Set<AuthKey.Public>
            @JvmField
            public val flags: Set<String>
            @JvmField
            public val maxStreams: Int?
            @JvmField
            public val ports: Set<TorSetting.LineItem>
            @JvmField
            public val destroyKeyOnJobCompletion: Boolean

            public companion object {

                /**
                 * Creates a command object that will instruct tor to generate keys for,
                 * and add to its runtime, a new Hidden Service.
                 *
                 * @see [BuilderScopeOnionAdd]
                 * */
                @JvmStatic
                public fun new(
                    type: KeyType.Address<*, *>,
                    block: ThisBlock<BuilderScopeOnionAdd>,
                ): Add = Add(null, type.configure(isExisting = false, block))

                /**
                 * Creates a command object that will instruct tor to add an existing
                 * Hidden Service to its runtime, for the provided [AddressKey.Private].
                 *
                 * @see [BuilderScopeOnionAdd]
                 * */
                @JvmStatic
                public fun existing(
                    key: AddressKey.Private,
                    block: ThisBlock<BuilderScopeOnionAdd>,
                ): Add = Add(key, key.type().configure(isExisting = true, block))
            }

            @Suppress("ConvertSecondaryConstructorToPrimary")
            private constructor(
                key: AddressKey.Private?,
                arguments: BuilderScopeOnionAdd.Arguments,
            ): super("ADD_ONION") {
                this.keyType = arguments.keyType
                this.key = key
                this.clientAuth = arguments.clientAuth
                this.flags = arguments.flags
                this.maxStreams = arguments.maxStreams
                this.ports = arguments.ports
                this.destroyKeyOnJobCompletion = if (key == null) {
                    false
                } else {
                    arguments.destroyKeyOnJobCompletion
                }
            }
        }

        /**
         * [control-spec#DEL_ONION](https://spec.torproject.org/control-spec/commands.html#del_onion)
         * */
        public class Delete: Unprivileged<Reply.Success> {

            @JvmField
            public val address: OnionAddress

            public constructor(key: AddressKey.Public): this(key.address())
            public constructor(address: OnionAddress): super("DEL_ONION") {
                this.address = address
            }
        }
    }

    public data object OnionClientAuth {

        /**
         * [control-spec#ONION_CLIENT_AUTH_ADD](https://spec.torproject.org/control-spec/commands.html#onion_client_auth_add)
         *
         * @see [BuilderScopeClientAuthAdd]
         * */
        public class Add: Unprivileged<Reply.Success> {

            @JvmField
            public val address: OnionAddress
            @JvmField
            public val key: AuthKey.Private
            @JvmField
            public val clientName: String?
            @JvmField
            public val flags: Set<String>
            @JvmField
            public val destroyKeyOnJobCompletion: Boolean

            public constructor(
                address: OnionAddress.V3,
                key: X25519.PrivateKey,
            ): this(address, key, BuilderScopeClientAuthAdd.Arguments.DEFAULT)

            public constructor(
                address: OnionAddress.V3,
                key: X25519.PrivateKey,
                block: ThisBlock<BuilderScopeClientAuthAdd>,
            ): this(address, key, BuilderScopeClientAuthAdd.configure(block))

            private constructor(
                address: OnionAddress,
                key: AuthKey.Private,
                arguments: BuilderScopeClientAuthAdd.Arguments,
            ): super("ONION_CLIENT_AUTH_ADD") {
                this.address = address
                this.key = key
                this.clientName = arguments.clientName
                this.flags = arguments.flags
                this.destroyKeyOnJobCompletion = arguments.destroyKeyOnJobCompletion
            }
        }

        /**
         * [control-spec#ONION_CLIENT_AUTH_REMOVE](https://spec.torproject.org/control-spec/commands.html#onion_client_auth_remove)
         * */
        public class Remove: Unprivileged<Reply.Success> {

            @JvmField
            public val address: OnionAddress

            public constructor(key: ED25519_V3.PublicKey): this(key.address())
            public constructor(address: OnionAddress.V3): this(address as OnionAddress)
            private constructor(address: OnionAddress): super("ONION_CLIENT_AUTH_REMOVE") {
                this.address = address
            }
        }

        /**
         * [control-spec#ONION_CLIENT_AUTH_VIEW](https://spec.torproject.org/control-spec/commands.html#onion_client_auth_view)
         *
         * @see [View.ALL]
         * */
        public class View: Unprivileged<List<ClientAuthEntry>> {

            @JvmField
            public val address: OnionAddress?

            public constructor(addressKey: ED25519_V3.PublicKey): this(addressKey.address())
            public constructor(address: OnionAddress.V3): this(address as OnionAddress)
            private constructor(address: OnionAddress?): super("ONION_CLIENT_AUTH_VIEW") {
                this.address = address
            }

            public companion object {

                /**
                 * Static instance where [address] is `null`, indicating that
                 * tor should return all Onion Client Auth information it has.
                 * */
                @JvmField
                public val ALL: View = View(address = null)
            }
        }
    }

    public data object Ownership {

        /**
         * [control-spec#DROPOWNERSHIP](https://spec.torproject.org/control-spec/commands.html#dropownership)
         * */
        public data object Drop: Privileged<Reply.Success.OK>("DROPOWNERSHIP")

        /**
         * [control-spec#TAKEOWNERSHIP](https://spec.torproject.org/control-spec/commands.html#takeownership)
         * */
        public data object Take: Privileged<Reply.Success.OK>("TAKEOWNERSHIP")
    }

//    /**
//     * [control-spec#POSTDESCRIPTOR](https://spec.torproject.org/control-spec/commands.html#postdescriptor)
//     * */
//    public class PostDescriptor: Unprivileged<String>("POSTDESCRIPTOR")

    /**
     * [control-spec#RESOLVE](https://spec.torproject.org/control-spec/commands.html#resolve)
     * */
    public class Resolve: Unprivileged<Reply.Success.OK> {

        @JvmField
        public val hostname: String
        @JvmField
        public val reverse: Boolean

        public constructor(address: IPAddress.V4, reverse: Boolean): this(address.canonicalHostName(), reverse)
        public constructor(hostname: String, reverse: Boolean): super("RESOLVE") {
            this.hostname = hostname
            this.reverse = reverse
        }
    }

    /**
     * [control-spec#SETEVENTS](https://spec.torproject.org/control-spec/commands.html#setevents)
     * */
    public class SetEvents: Unprivileged<Reply.Success.OK> {

        @JvmField
        public val events: Set<TorEvent>

        public constructor(): this(emptySet())
        public constructor(event: TorEvent): this(immutableSetOf(event))
        public constructor(vararg events: TorEvent): this(immutableSetOf(*events))
        public constructor(events: Collection<TorEvent>): super("SETEVENTS") {
            this.events = events.toImmutableSet()
        }
    }

    /**
     * [control-spec#SIGNAL](https://spec.torproject.org/control-spec/commands.html#signal)
     * */
    public data object Signal {

        public data object Dump: Unprivileged<Reply.Success.OK>("SIGNAL")
        public data object Debug: Unprivileged<Reply.Success.OK>("SIGNAL")

        /**
         * See [io.matthewnelson.kmp.tor.runtime.RuntimeEvent.EXECUTE.CMD.observeSignalNewNym]
         * */
        public data object NewNym: Unprivileged<Reply.Success.OK>("SIGNAL")

        public data object ClearDnsCache: Unprivileged<Reply.Success.OK>("SIGNAL")
        public data object Heartbeat: Unprivileged<Reply.Success.OK>("SIGNAL")
        public data object Active: Unprivileged<Reply.Success.OK>("SIGNAL")
        public data object Dormant: Unprivileged<Reply.Success.OK>("SIGNAL")

        public data object Reload: Privileged<Reply.Success.OK>("SIGNAL")
        public data object Shutdown: Privileged<Reply.Success.OK>("SIGNAL")
        public data object Halt: Privileged<Reply.Success.OK>("SIGNAL")
    }

//    public data object Stream {
//
//        /**
//         * [control-spec#ATTACHSTREAM](https://spec.torproject.org/control-spec/commands.html#attachstream)
//         * */
//        public class Attach: Unprivileged<Reply.Success.OK>("ATTACHSTREAM")
//
//        /**
//         * [control-spec#CLOSESTREAM](https://spec.torproject.org/control-spec/commands.html#closestream)
//         * */
//        public class Close: Unprivileged<Reply.Success.OK>("CLOSESTREAM")
//
//        /**
//         * [control-spec#REDIRECTSTREAM](https://spec.torproject.org/control-spec/commands.html#redirectstream)
//         * */
//        public class Redirect: Unprivileged<Reply.Success.OK>("REDIRECTSTREAM")
//    }

//    /**
//     * [control-spec#USEFEATURE](https://spec.torproject.org/control-spec/commands.html#usefeature)
//     * */
//    public class UseFeature: Unprivileged<Reply.Success.OK>("USEFEATURE")

    /**
     * A [TorCmd] whose use is restricted to only that of the control
     * connection, and not with TorRuntime.
     * */
    public sealed class Privileged<Success: Any>(keyword: String): TorCmd<Success>(keyword) {

        /**
         * Base interface for implementations that process [Privileged] type [TorCmd]
         *
         * **NOTE:** Implementors **MUST** process the command on a different
         * thread than what [enqueue] is called from for Jvm & Native.
         * */
        public interface Processor: Unprivileged.Processor {

            /**
             * Adds the [cmd] to the queue.
             *
             * **NOTE:** If the returned [EnqueuedJob] gets cancelled,
             * [onFailure] will be invoked with [CancellationException]
             * indicating normal behavior.
             *
             * **NOTE:** If the returned [EnqueuedJob] gets interrupted,
             * [onFailure] will be invoked with [InterruptedException].
             * This can occur when the [Processor] implementation is
             * shutdown and the job is awaiting execution.
             *
             * @return [EnqueuedJob]
             * @see [Reply.Error]
             * @see [OnFailure]
             * @see [OnSuccess]
             * @see [io.matthewnelson.kmp.tor.runtime.core.util.executeSync]
             * @see [io.matthewnelson.kmp.tor.runtime.core.util.executeAsync]
             * */
            public fun <Success: Any> enqueue(
                cmd: Privileged<Success>,
                onFailure: OnFailure,
                onSuccess: OnSuccess<Success>,
            ): EnqueuedJob
        }
    }

    /**
     * A [TorCmd] that is capable of being utilized with both the
     * control connection and TorRuntime (which passes it through to
     * the underlying control connection).
     * */
    public sealed class Unprivileged<Success: Any>(keyword: String): TorCmd<Success>(keyword) {

        /**
         * Base interface for implementations that process [Unprivileged] type [TorCmd]
         *
         * **NOTE:** Implementors **MUST** process the command on a different
         * thread than what [enqueue] is called from for Jvm & Native.
         * */
        public interface Processor {

            /**
             * Adds the [cmd] to the queue.
             *
             * **NOTE:** If the returned [EnqueuedJob] gets cancelled,
             * [onFailure] will be invoked with [CancellationException]
             * indicating normal behavior.
             *
             * **NOTE:** If the returned [EnqueuedJob] gets interrupted,
             * [onFailure] will be invoked with [InterruptedException].
             * This can occur when the [Processor] implementation is
             * shutdown and the job is awaiting execution.
             *
             * @return [EnqueuedJob]
             * @see [OnFailure]
             * @see [OnSuccess]
             * @see [io.matthewnelson.kmp.tor.runtime.core.util.executeSync]
             * @see [io.matthewnelson.kmp.tor.runtime.core.util.executeAsync]
             * */
            public fun <Success: Any> enqueue(
                cmd: Unprivileged<Success>,
                onFailure: OnFailure,
                onSuccess: OnSuccess<Success>,
            ): EnqueuedJob
        }
    }

    /** @suppress */
    public final override fun toString(): String = keyword
}
