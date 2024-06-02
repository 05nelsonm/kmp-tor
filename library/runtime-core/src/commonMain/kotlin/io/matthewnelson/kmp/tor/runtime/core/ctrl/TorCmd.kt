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
import io.matthewnelson.kmp.tor.runtime.core.address.IPAddress
import io.matthewnelson.kmp.tor.runtime.core.address.OnionAddress
import io.matthewnelson.kmp.tor.runtime.core.key.AddressKey
import io.matthewnelson.kmp.tor.runtime.core.key.ED25519_V3
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmField

/**
 * Commands to interact with tor via its control connection, as
 * described in [control-spec](https://spec.torproject.org/control-spec/index.html).
 *
 * Commands are separated into 2 categories, [Privileged] and
 * [Unprivileged]. This is unique to `kmp-tor` only, not tor.
 * The purpose is to allow abstractions to be built on top of a
 * control connection implementation (such as `TorRuntime`) that
 * manage the connection and allow pass-through of [Unprivileged]
 * commands, while internally using [Privileged] commands to set
 * up and maintain the connection.
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
     * "AUTHENTICATE"
     *
     * [docs](https://spec.torproject.org/control-spec/commands.html#authenticate)
     * */
    public class Authenticate: Privileged<Reply.Success.OK> {

        @JvmField
        public val hex: String

        /** No Password (i.e. unauthenticated control connection) */
        public constructor(): this(ByteArray(0))

        /** Un-Hashed (raw) Password for HashedControlPassword (e.g. `"Hello World!"`) */
        public constructor(password: String): this(password.encodeToByteArray())

        /** Cookie authentication bytes (or password UTF-8 encoded to bytes) */
        public constructor(cookie: ByteArray): super("AUTHENTICATE") {
            hex = if (cookie.isEmpty()) "" else cookie.encodeToString(Base16())
        }
    }

//    /**
//     * "AUTHCHALLENGE"
//     *
//     * [docs](https://spec.torproject.org/control-spec/commands.html#authchallenge)
//     * */
//    public class ChallengeAuth: Privileged<Reply.Success.OK>("AUTHCHALLENGE")

//    public data object Circuit {
//
//        /**
//         * "CLOSECIRCUIT"
//         *
//         * [docs](https://spec.torproject.org/control-spec/commands.html#closecircuit)
//         * */
//        public class Close: Unprivileged<Reply.Success.OK>("CLOSECIRCUIT")
//
//        /**
//         * "EXTENDCIRCUIT"
//         *
//         * [docs](https://spec.torproject.org/control-spec/commands.html#extendcircuit)
//         * */
//        public class Extend: Unprivileged<String>("EXTENDCIRCUIT")
//
//        /**
//         * "SETCIRCUITPURPOSE"
//         *
//         * [docs](https://spec.torproject.org/control-spec/commands.html#setcircuitpurpose)
//         * */
//        public class SetPurpose: Unprivileged<Reply.Success.OK>("SETCIRCUITPURPOSE")
//    }

    public data object Config {

        /**
         * "GETCONF"
         *
         * [docs](https://spec.torproject.org/control-spec/commands.html#getconf)
         * */
        public class Get: Unprivileged<List<ConfigEntry>> {

            @JvmField
            public val keywords: kotlin.collections.Set<TorConfig.Keyword>

            public constructor(keyword: TorConfig.Keyword): this(immutableSetOf(keyword))

            public constructor(vararg keywords: TorConfig.Keyword): this(immutableSetOf(*keywords))

            public constructor(keywords: Collection<TorConfig.Keyword>): super("GETCONF") {
                this.keywords = keywords.toImmutableSet()
            }
        }

        /**
         * "+LOADCONF"
         *
         * [docs](https://spec.torproject.org/control-spec/commands.html#loadconf)
         * */
        public class Load(configText: String): Privileged<Reply.Success.OK>("LOADCONF") {

            @JvmField
            public val configText: String = buildString {
                for (line in configText.lines()) {
                    val isCommentOrBlank = run {
                        val i = line.indexOfFirst { !it.isWhitespace() }
                        if (i == -1) true else line[i] == '#'
                    }

                    if (isCommentOrBlank) continue
                    if (isNotEmpty()) appendLine()
                    append(line)
                }
            }
        }

        /**
         * "RESETCONF"
         *
         * [docs](https://spec.torproject.org/control-spec/commands.html#resetconf)
         * */
        public class Reset: Unprivileged<Reply.Success.OK> {

            @JvmField
            public val keywords: kotlin.collections.Set<TorConfig.Keyword>

            public constructor(keyword: TorConfig.Keyword): this(immutableSetOf(keyword))

            public constructor(vararg keywords: TorConfig.Keyword): this(immutableSetOf(*keywords))

            public constructor(keywords: Collection<TorConfig.Keyword>): super("RESETCONF") {
                this.keywords = keywords.toImmutableSet()
            }
        }

        /**
         * "SAVECONF"
         *
         * [docs](https://spec.torproject.org/control-spec/commands.html#saveconf)
         * */
        public class Save(
            @JvmField
            public val force: Boolean,
        ): Privileged<Reply.Success.OK>("SAVECONF") {

            /** Default of [force] = `false` */
            public constructor(): this(force = false)
        }

        /**
         * "SETCONF"
         *
         * [docs](https://spec.torproject.org/control-spec/commands.html#setconf)
         * */
        public class Set: Unprivileged<Reply.Success.OK> {

            @JvmField
            public val settings: kotlin.collections.Set<TorConfig.Setting>

            public constructor(setting: TorConfig.Setting): this(immutableSetOf(setting))

            public constructor(vararg settings: TorConfig.Setting): this(immutableSetOf(*settings))

            public constructor(settings: Collection<TorConfig.Setting>): super("SETCONF") {
                this.settings = settings.toImmutableSet()
            }
        }
    }

    /**
     * "DROPGUARDS"
     *
     * [docs](https://spec.torproject.org/control-spec/commands.html#dropguards)
     * */
    public data object DropGuards: Unprivileged<Reply.Success.OK>("DROPGUARDS")

    public data object Hs {

        /**
         * "HSFETCH"
         *
         * [docs](https://spec.torproject.org/control-spec/commands.html#hsfetch)
         * */
        public class Fetch: Unprivileged<Reply.Success.OK> {

            @JvmField
            public val address: OnionAddress
            @JvmField
            public val servers: Set<String>

            public constructor(key: AddressKey.Public): this(key.address())
            public constructor(address: OnionAddress): this(address, emptySet())

            public constructor(key: AddressKey.Public, server: String): this(key.address(), server)
            public constructor(address: OnionAddress, server: String): this(address, immutableSetOf(server))

            public constructor(key: AddressKey.Public, vararg servers: String): this(key, immutableSetOf(*servers))
            public constructor(address: OnionAddress, vararg servers: String): this(address, immutableSetOf(*servers))

            public constructor(key: AddressKey.Public, servers: Collection<String>): this(key.address(), servers)
            public constructor(address: OnionAddress, servers: Collection<String>): super("HSFETCH") {
                this.address = address
                this.servers = servers.toImmutableSet()
            }
        }

//        /**
//         * "+HSPOST"
//         *
//         * [docs](https://spec.torproject.org/control-spec/commands.html#hspost)
//         * */
//        public class Post: Unprivileged<Reply.Success.OK>("HSPOST")
    }

    public data object Info {

        /**
         * "GETINFO"
         *
         * [docs](https://spec.torproject.org/control-spec/commands.html#getinfo)
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
//         * "PROTOCOLINFO"
//         *
//         * [docs](https://spec.torproject.org/control-spec/commands.html#protocolinfo)
//         * */
//        public class Protocol: Privileged<Map<String, String>>("PROTOCOLINFO")
    }

    /**
     * "MAPADDRESS"
     *
     * [docs](https://spec.torproject.org/control-spec/commands.html#mapaddress)
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
         * "ADD_ONION"
         *
         * [docs](https://spec.torproject.org/control-spec/commands.html#add_onion)
         * */
        public class Add private constructor(
            // TODO: Issue #419
        ): Unprivileged<HiddenServiceEntry>("ADD_ONION")

        /**
         * "DEL_ONION"
         *
         * [docs](https://spec.torproject.org/control-spec/commands.html#del_onion)
         * */
        public class Delete(
            @JvmField
            public val address: OnionAddress,
        ): Unprivileged<Reply.Success>("DEL_ONION") {

            public constructor(key: AddressKey.Public): this(key.address())
        }
    }

    public data object OnionClientAuth {

        /**
         * "ONION_CLIENT_AUTH_ADD"
         *
         * [docs](https://spec.torproject.org/control-spec/commands.html#onion_client_auth_add)
         * */
        public class Add private constructor(
            // TODO: Issue #420
        ): Unprivileged<Reply.Success>("ONION_CLIENT_AUTH_ADD")

        /**
         * "ONION_CLIENT_AUTH_REMOVE"
         *
         * [docs](https://spec.torproject.org/control-spec/commands.html#onion_client_auth_remove)
         * */
        public class Remove private constructor(
            @JvmField
            public val address: OnionAddress,
        ): Unprivileged<Reply.Success>("ONION_CLIENT_AUTH_REMOVE") {

            public constructor(key: ED25519_V3.PublicKey): this(key.address())

            public constructor(address: OnionAddress.V3): this(address as OnionAddress)
        }

        /**
         * "ONION_CLIENT_AUTH_VIEW"
         *
         * [docs](https://spec.torproject.org/control-spec/commands.html#onion_client_auth_view)
         * */
        public class View: Unprivileged<List<ClientAuthEntry>> {

            @JvmField
            public val address: OnionAddress?

            public constructor(key: ED25519_V3.PublicKey): this(key.address())

            public constructor(address: OnionAddress.V3): this(address as OnionAddress)

            private constructor(address: OnionAddress?): super("ONION_CLIENT_AUTH_VIEW") {
                this.address = address
            }

            public companion object {

                @JvmField
                public val ALL: View = View(null)
            }
        }
    }

    public data object Ownership {

        /**
         * "DROPOWNERSHIP"
         *
         * [docs](https://spec.torproject.org/control-spec/commands.html#dropownership)
         * */
        public data object Drop: Privileged<Reply.Success.OK>("DROPOWNERSHIP")

        /**
         * "TAKEOWNERSHIP"
         *
         * [docs](https://spec.torproject.org/control-spec/commands.html#takeownership)
         * */
        public data object Take: Privileged<Reply.Success.OK>("TAKEOWNERSHIP")
    }

//    /**
//     * "+POSTDESCRIPTOR"
//     *
//     * [docs](https://spec.torproject.org/control-spec/commands.html#postdescriptor)
//     * */
//    public class PostDescriptor: Unprivileged<String>("POSTDESCRIPTOR")

    /**
     * "RESOLVE"
     *
     * [docs](https://spec.torproject.org/control-spec/commands.html#resolve)
     * */
    public class Resolve(
        @JvmField
        public val hostname: String,
        @JvmField
        public val reverse: Boolean,
    ): Unprivileged<Reply.Success.OK>("RESOLVE") {

        public constructor(
            address: IPAddress.V4,
            reverse: Boolean,
        ): this(address.canonicalHostName(), reverse)
    }

    /**
     * "SETEVENTS"
     *
     * [docs](https://spec.torproject.org/control-spec/commands.html#setevents)
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
     * "SIGNAL"
     *
     * [docs](https://spec.torproject.org/control-spec/commands.html#signal)
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

        public data object Reload: Unprivileged<Reply.Success.OK>("SIGNAL")
        public data object Shutdown: Privileged<Reply.Success.OK>("SIGNAL")
        public data object Halt: Privileged<Reply.Success.OK>("SIGNAL")
    }

//    public data object Stream {
//
//        /**
//         * "ATTACHSTREAM"
//         *
//         * [ATTACHSTREAM](https://spec.torproject.org/control-spec/commands.html#attachstream)
//         * */
//        public class Attach: Unprivileged<Reply.Success.OK>("ATTACHSTREAM")
//
//        /**
//         * "CLOSESTREAM"
//         *
//         * [CLOSESTREAM](https://spec.torproject.org/control-spec/commands.html#closestream)
//         * */
//        public class Close: Unprivileged<Reply.Success.OK>("CLOSESTREAM")
//
//        /**
//         * "REDIRECTSTREAM"
//         *
//         * [REDIRECTSTREAM](https://spec.torproject.org/control-spec/commands.html#redirectstream)
//         * */
//        public class Redirect: Unprivileged<Reply.Success.OK>("REDIRECTSTREAM")
//    }

//    /**
//     * "USEFEATURE"
//     *
//     * [USEFEATURE](https://spec.torproject.org/control-spec/commands.html#usefeature)
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

    final override fun toString(): String = keyword
}
