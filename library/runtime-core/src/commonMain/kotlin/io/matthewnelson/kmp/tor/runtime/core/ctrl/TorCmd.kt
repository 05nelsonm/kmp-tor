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
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.tor.runtime.core.ItBlock
import io.matthewnelson.kmp.tor.runtime.core.TorJob
import io.matthewnelson.kmp.tor.runtime.core.TorConfig
import io.matthewnelson.kmp.tor.runtime.core.TorEvent
import io.matthewnelson.kmp.tor.runtime.core.address.IPAddress
import io.matthewnelson.kmp.tor.runtime.core.address.OnionAddress
import io.matthewnelson.kmp.tor.runtime.core.key.AddressKey
import io.matthewnelson.kmp.tor.runtime.core.key.ED25519_V3
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resumeWithException
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * Commands to interact with tor via its control connection.
 *
 * Commands are separated into 2 categories, [Privileged] and
 * [Unprivileged]. This is unique to KmpTor only, not tor. The
 * purpose is to allow abstractions to be built on top of a
 * control connection (such as TorRuntime) that manage the
 * connection and allow pass-through of [Unprivileged] commands,
 * while internally using [Privileged] commands to set up and
 * maintain the connection.
 *
 * @see [Privileged.Processor]
 * @see [Unprivileged.Processor]
 * */
public sealed class TorCmd<Response: Any> private constructor(
    @JvmField
    public val name: Name,
) {

    /**
     * "AUTHENTICATE" [ SP 1*HEXDIG / QuotedString ] CRLF
     *
     * [docs](https://torproject.gitlab.io/torspec/control-spec/#authenticate)
     * */
    public class Authenticate private constructor(
        @JvmField
        public val key: String,
    ): Privileged<Unit>(Name.AUTHENTICATE) {

        public companion object {

            @JvmField
            public val NONE: Authenticate = Authenticate("")

            @JvmStatic
            public fun cookie(
                bytes: ByteArray
            ): Authenticate = if (bytes.isEmpty()) NONE else Authenticate(bytes.encodeToString(Base16()))

            @JvmStatic
            public fun password(
                value: String,
            ): Authenticate = Authenticate("\"$value\"")
        }
    }

//    /**
//     * "AUTHCHALLENGE" SP "SAFECOOKIE"
//     *                 SP ClientNonce
//     *                 CRLF
//     *
//     * ClientNonce = 2*HEXDIG / QuotedString
//     *
//     * [docs](https://torproject.gitlab.io/torspec/control-spec/#authchallenge)
//     * */
//    public class ChallengeAuth: Privileged<Unit>(Name.AUTHCHALLENGE)

//    public data object Circuit {
//
//        /**
//         * "CLOSECIRCUIT" SP CircuitID *(SP Flag) CRLF
//         *
//         * Flag = "IfUnused"
//         *
//         * [docs](https://torproject.gitlab.io/torspec/control-spec/#closecircuit)
//         * */
//        public class Close: Unprivileged<Unit>(Name.CLOSECIRCUIT)
//
//        /**
//         * "EXTENDCIRCUIT" SP CircuitID
//         *                 [SP ServerSpec *("," ServerSpec)]
//         *                 [SP "purpose=" Purpose] CRLF
//         *
//         * [docs](https://torproject.gitlab.io/torspec/control-spec/#extendcircuit)
//         * */
//        public class Extend: Unprivileged<String>(Name.EXTENDCIRCUIT)
//
//        /**
//         * "SETCIRCUITPURPOSE" SP CircuitID SP "purpose=" Purpose CRLF
//         *
//         * [docs](https://torproject.gitlab.io/torspec/control-spec/#setcircuitpurpose)
//         * */
//        public class SetPurpose: Unprivileged<Unit>(Name.SETCIRCUITPURPOSE)
//    }

    public data object Config {

        /**
         * "GETCONF" 1*(SP keyword) CRLF
         *
         * [docs](https://torproject.gitlab.io/torspec/control-spec/#getconf)
         * */
        public class Get: Unprivileged<List<ConfigEntry>> {

            @JvmField
            public val keywords: kotlin.collections.Set<TorConfig.Keyword>

            public constructor(keyword: TorConfig.Keyword): super(Name.GETCONF) {
                this.keywords = immutableSetOf(keyword)
            }

            public constructor(keywords: Collection<TorConfig.Keyword>): super(Name.GETCONF) {
                this.keywords = keywords.toImmutableSet()
            }

            public constructor(vararg keywords: TorConfig.Keyword): super(Name.GETCONF) {
                this.keywords = immutableSetOf(*keywords)
            }
        }

        /**
         * "+LOADCONF" CRLF ConfigText CRLF "." CRLF
         *
         * [docs](https://torproject.gitlab.io/torspec/control-spec/#loadconf)
         * */
        public class Load(
            @JvmField
            public val config: TorConfig,
        ): Privileged<Unit>(Name.LOADCONF)

        /**
         * "RESETCONF" 1*(SP keyword ["=" String]) CRLF
         *
         * [docs](https://torproject.gitlab.io/torspec/control-spec/#resetconf)
         * */
        public class Reset: Unprivileged<Unit> {

            @JvmField
            public val keywords: kotlin.collections.Set<TorConfig.Keyword>

            public constructor(keyword: TorConfig.Keyword): super(Name.RESETCONF) {
                this.keywords = immutableSetOf(keyword)
            }

            public constructor(keywords: Collection<TorConfig.Keyword>): super(Name.RESETCONF) {
                this.keywords = keywords.toImmutableSet()
            }

            public constructor(vararg keywords: TorConfig.Keyword): super(Name.RESETCONF) {
                this.keywords = immutableSetOf(*keywords)
            }
        }

        /**
         * "SAVECONF" [SP "FORCE"] CRLF
         *
         * [docs](https://torproject.gitlab.io/torspec/control-spec/#saveconf)
         * */
        public class Save(
            @JvmField
            public val force: Boolean,
        ): Unprivileged<Unit>(Name.SAVECONF) {

            public constructor(): this(force = false)
        }

        /**
         * "SETCONF" 1*(SP keyword ["=" value]) CRLF
         *
         * value = String / QuotedString
         *
         * [docs](https://torproject.gitlab.io/torspec/control-spec/#setconf)
         * */
        public class Set: Unprivileged<Unit> {

            @JvmField
            public val settings: kotlin.collections.Set<TorConfig.Setting>

            public constructor(setting: TorConfig.Setting): super(Name.SETCONF) {
                this.settings = immutableSetOf(setting)
            }

            public constructor(settings: Collection<TorConfig.Setting>): super(Name.SETCONF) {
                this.settings = settings.toImmutableSet()
            }

            public constructor(vararg settings: TorConfig.Setting): super(Name.SETCONF) {
                this.settings = immutableSetOf(*settings)
            }
        }
    }

    /**
     * "DROPGUARDS" CRLF
     *
     * [docs](https://torproject.gitlab.io/torspec/control-spec/#dropguards)
     * */
    public data object DropGuards: Unprivileged<Unit>(Name.DROPGUARDS)

    public data object Hs {

        /**
         * "HSFETCH" SP (HSAddress / "v" Version "-" DescId)
         *           *[SP "SERVER=" Server] CRLF
         *
         * HSAddress = 16*Base32Character / 56*Base32Character
         *
         * Version = "2" / "3"
         *
         * DescId = 32*Base32Character
         *
         * Server = LongName
         *
         * [docs](https://torproject.gitlab.io/torspec/control-spec/#hsfetch)
         * */
        public class Fetch(
            @JvmField
            public val address: OnionAddress,
            // TODO: Set<Server.Fingerprint>,
        ): Unprivileged<Unit>(Name.HSFETCH) {

            public constructor(key: AddressKey.Public): this(key.address())
        }

//        /**
//         * "+HSPOST" *[SP "SERVER=" Server] [SP "HSADDRESS=" HSAddress]
//         *           CRLF Descriptor CRLF "." CRLF
//         *
//         * Server = LongName
//         * HSAddress = 56*Base32Character
//         * Descriptor = The text of the descriptor formatted as specified
//         *              in rend-spec.txt section 1.3.
//         *
//         * [docs](https://torproject.gitlab.io/torspec/control-spec/#hspost)
//         * */
//        public class Post: Unprivileged<Unit>(Name.HSPOST)
    }

    public data object Info {

        /**
         * "GETINFO" 1*(SP keyword) CRLF
         *
         * [docs](https://torproject.gitlab.io/torspec/control-spec/#getinfo)
         * */
        public class Get: Unprivileged<Map<String, String>> {

            @JvmField
            public val keywords: Set<String>

            public constructor(keyword: String): super(Name.GETINFO) {
                this.keywords = immutableSetOf(keyword)
            }

            public constructor(keywords: Collection<String>): super(Name.GETINFO) {
                this.keywords = keywords.toImmutableSet()
            }

            public constructor(vararg keywords: String): super(Name.GETINFO) {
                this.keywords = immutableSetOf(*keywords)
            }
        }

//        /**
//         * "PROTOCOLINFO" *(SP PIVERSION) CRLF
//         *
//         * [docs](https://torproject.gitlab.io/torspec/control-spec/#protocolinfo)
//         * */
//        public class Protocol: Privileged<Map<String, String>>(Name.PROTOCOLINFO)
    }

    /**
     * "MAPADDRESS" 1*(Address "=" Address SP) CRLF
     *
     * [docs](https://torproject.gitlab.io/torspec/control-spec/#mapaddress)
     * */
    public class MapAddress: Unprivileged<Set<AddressMapping.Result>> {

        @JvmField
        public val mappings: Set<AddressMapping>

        public constructor(mapping: AddressMapping): super(Name.MAPADDRESS) {
            this.mappings = immutableSetOf(mapping)
        }

        public constructor(mappings: Collection<AddressMapping>): super(Name.MAPADDRESS) {
            this.mappings = mappings.toImmutableSet()
        }

        public constructor(vararg mappings: AddressMapping): super(Name.MAPADDRESS) {
            this.mappings = immutableSetOf(*mappings)
        }
    }

    public data object Onion {

        /**
         * "ADD_ONION" SP KeyType ":" KeyBlob
         *           [SP "Flags=" Flag *("," Flag)]
         *           [SP "MaxStreams=" NumStreams]
         *           1*(SP "Port=" VirtPort ["," Target])
         *           *(SP "ClientAuth=" ClientName [":" ClientBlob]) CRLF
         *
         * KeyType =
         *   - "NEW"     / ; The server should generate a key of algorithm KeyBlob
         *   - "RSA1024" / ; The server should use the 1024 bit RSA key provided in as KeyBlob (v2).
         *   - "ED25519-V3"; The server should use the ed25519 v3 key provided in as KeyBlob (v3).
         *
         * KeyBlob =
         *   - "BEST"    / ; The server should generate a key using the "best" supported algorithm
         *                 (KeyType == "NEW"). [As of 0.4.2.3-alpha, ED25519-V3 is used]
         *   - "RSA1024" / ; The server should generate a 1024 bit RSA key (KeyType == "NEW") (v2).
         *   - "ED25519-V3"; The server should generate an ed25519 private key (KeyType == "NEW") (v3).
         *   - String      ; A serialized private key (without whitespace)
         *
         * Flag =
         *   - "DiscardPK" / ; The server should not include the newly generated private key as part
         *                   of the response.
         *   - "Detach"    / ; Do not associate the newly created Onion Service to the current control
         *                   connection.
         *   - "BasicAuth" / ; Client authorization is required using the "basic" method (v2 only).
         *   - "NonAnonymous" /; Add a non-anonymous Single Onion Service. Tor checks this flag matches
         *                     its configured hidden service anonymity mode.
         *   - "MaxStreamsCloseCircuit"; Close the circuit is the maximum streams allowed is reached.
         *
         * NumStreams = A value between 0 and 65535 which is used as the maximum
         *              streams that can be attached on a rendezvous circuit. Setting
         *              it to 0 means unlimited which is also the default behavior.
         *
         * VirtPort = The virtual TCP Port for the Onion Service (As in the
         *            HiddenServicePort "VIRTPORT" argument).
         *
         *  Target = The (optional) target for the given VirtPort (As in the
         *          optional HiddenServicePort "TARGET" argument).
         *
         *  ClientName = An identifier 1 to 16 characters long, using only
         *              characters in A-Za-z0-9+-_ (no spaces) (v2 only).
         *
         *  ClientBlob = Authorization data for the client, in an opaque format
         *              specific to the authorization method (v2 only).
         *
         * [docs](https://torproject.gitlab.io/torspec/control-spec/#add_onion)
         * */
        public class Add private constructor(
            // TODO: Builder
        ): Unprivileged<HiddenServiceEntry>(Name.ADD_ONION)

        /**
         * "DEL_ONION" SP ServiceID CRLF
         *
         * ServiceID = The Onion Service address without the trailing ".onion" suffix
         *
         * [docs](https://torproject.gitlab.io/torspec/control-spec/#del_onion)
         * */
        public class Delete(
            @JvmField
            public val address: OnionAddress,
        ): Unprivileged<Unit>(Name.DEL_ONION) {

            public constructor(key: AddressKey.Public): this(key.address())
        }
    }

    public data object OnionClientAuth {

        /**
         * "ONION_CLIENT_AUTH_ADD" SP HSAddress
         *                         SP KeyType ":" PrivateKeyBlob
         *                         [SP "ClientName=" Nickname]
         *                         [SP "Flags=" TYPE] CRLF
         *
         * HSAddress = 56*Base32Character
         *
         * KeyType = "x25519" is the only one supported right now
         *
         * PrivateKeyBlob = base64 encoding of x25519 key
         *
         * FLAGS is a comma-separated tuple of flags for this new client. For now, the
         * currently supported flags are:
         *
         *   - "Permanent" - This client's credentials should be stored in the filesystem.
         *                 Filename will be in the following format:
         *
         *                     `HSAddress.auth_private`
         *
         *                 where `HSAddress` is the 56*Base32Character onion address w/o
         *                 the .onion appended.
         *
         *                 If this is not set, the client's credentials are ephemeral
         *                 and stored in memory.
         *
         * [docs](https://torproject.gitlab.io/torspec/control-spec/#onion_client_auth_add)
         * */
        public class Add private constructor(
            // TODO
        ): Unprivileged<Unit>(Name.ONION_CLIENT_AUTH_ADD)

        /**
         * "ONION_CLIENT_AUTH_REMOVE" SP HSAddress
         *
         * KeyType = “x25519” is the only one supported right now
         *
         * [docs](https://torproject.gitlab.io/torspec/control-spec/#onion_client_auth_remove)
         * */
        public class Remove private constructor(
            @JvmField
            public val address: OnionAddress,
        ): Unprivileged<Unit>(Name.ONION_CLIENT_AUTH_REMOVE) {

            public constructor(address: OnionAddress.V3): this(address as OnionAddress)
            public constructor(key: ED25519_V3.PublicKey): this(key.address())
        }

        /**
         * "ONION_CLIENT_AUTH_VIEW" [SP HSAddress] CRLF
         *
         * [docs](https://torproject.gitlab.io/torspec/control-spec/#onion_client_auth_view)
         * */
        public class View: Unprivileged<List<ClientAuthEntry>> {

            @JvmField
            public val address: OnionAddress?

            private constructor(): super(Name.ONION_CLIENT_AUTH_VIEW) {
                this.address = null
            }

            public constructor(address: OnionAddress.V3): super(Name.ONION_CLIENT_AUTH_VIEW) {
                this.address = address
            }

            public constructor(key: ED25519_V3.PublicKey): super(Name.ONION_CLIENT_AUTH_VIEW) {
                this.address = key.address()
            }

            public companion object {

                @JvmField
                public val ALL: View = View()
            }
        }
    }

    public data object Ownership {

        /**
         * "DROPOWNERSHIP" CRLF
         *
         * [docs](https://torproject.gitlab.io/torspec/control-spec/#dropownership)
         * */
        public data object Drop: Privileged<Unit>(Name.DROPOWNERSHIP)

        /**
         * "TAKEOWNERSHIP" CRLF
         *
         * [docs](https://torproject.gitlab.io/torspec/control-spec/#takeownership)
         * */
        public data object Take: Privileged<Unit>(Name.TAKEOWNERSHIP)
    }

//    /**
//     * "+POSTDESCRIPTOR" [SP "purpose=" Purpose] [SP "cache=" Cache]
//     *                   CRLF Descriptor CRLF "." CRLF
//     *
//     * [docs](https://torproject.gitlab.io/torspec/control-spec/#postdescriptor)
//     * */
//    public class PostDescriptor: Unprivileged<String>(Name.POSTDESCRIPTOR)

    /**
     * "RESOLVE" *Option *Address CRLF
     *
     *  Option = "mode=reverse"
     *
     *  Address = a hostname or IPv4 address
     *
     * [docs](https://torproject.gitlab.io/torspec/control-spec/#resolve)
     * */
    public class Resolve(
        @JvmField
        public val hostname: String,
        @JvmField
        public val reverse: Boolean,
    ): Unprivileged<Unit>(Name.RESOLVE) {

        public constructor(
            address: IPAddress.V4,
            reverse: Boolean,
        ): this(address.value, reverse)
    }

    /**
     * "SETEVENTS" [SP "EXTENDED"] *(SP EventCode) CRLF
     *
     * Any events not listed in the SETEVENTS line are turned off; thus, sending
     * SETEVENTS with an empty body turns off all event reporting.
     *
     * [docs](https://torproject.gitlab.io/torspec/control-spec/#setevents)
     * */
    public class SetEvents: Unprivileged<Unit> {

        @JvmField
        public val events: Set<TorEvent>

        public constructor(event: TorEvent): super(Name.SETEVENTS) {
            this.events = immutableSetOf(event)
        }

        public constructor(events: Collection<TorEvent>): super(Name.SETEVENTS) {
            this.events = events.toImmutableSet()
        }

        public constructor(vararg events: TorEvent): super(Name.SETEVENTS) {
            this.events = immutableSetOf(*events)
        }
    }

    /**
     * "SIGNAL" SP Signal CRLF
     *
     * Signal = "RELOAD" / "SHUTDOWN" / "DUMP" / "DEBUG" / "HALT" /
     *          "HUP" / "INT" / "USR1" / "USR2" / "TERM" / "NEWNYM" /
     *          "CLEARDNSCACHE" / "HEARTBEAT" / "ACTIVE" / "DORMANT"
     *
     * [docs](https://torproject.gitlab.io/torspec/control-spec/#signal)
     * */
    public data object Signal {

        public data object Reload: Unprivileged<Unit>(Name.SIGNAL)
        public data object Dump: Unprivileged<Unit>(Name.SIGNAL)
        public data object Debug: Unprivileged<Unit>(Name.SIGNAL)
        public data object NewNym: Unprivileged<Unit>(Name.SIGNAL)
        public data object ClearDnsCache: Unprivileged<Unit>(Name.SIGNAL)
        public data object Heartbeat: Unprivileged<Unit>(Name.SIGNAL)
        public data object Active: Unprivileged<Unit>(Name.SIGNAL)
        public data object Dormant: Unprivileged<Unit>(Name.SIGNAL)

        public data object Shutdown: Privileged<Unit>(Name.SIGNAL)
        public data object Halt: Privileged<Unit>(Name.SIGNAL)
    }

//    public data object Stream {
//
//        /**
//         * "ATTACHSTREAM" SP StreamID SP CircuitID [SP "HOP=" HopNum] CRLF
//         *
//         * [ATTACHSTREAM](https://torproject.gitlab.io/torspec/control-spec/#attachstream)
//         * */
//        public class Attach: Unprivileged<Unit>(Name.ATTACHSTREAM)
//
//        /**
//         * "CLOSESTREAM" SP StreamID SP Reason *(SP Flag) CRLF
//         *
//         * [CLOSESTREAM](https://torproject.gitlab.io/torspec/control-spec/#closestream)
//         * */
//        public class Close: Unprivileged<Unit>(Name.CLOSESTREAM)
//
//        /**
//         * "REDIRECTSTREAM" SP StreamID SP Address [SP Port] CRLF
//         *
//         * [REDIRECTSTREAM](https://torproject.gitlab.io/torspec/control-spec/#redirectstream)
//         * */
//        public class Redirect: Unprivileged<Unit>(Name.REDIRECTSTREAM)
//    }

//    /**
//     * "USEFEATURE" *(SP FeatureName) CRLF
//     *
//     * FeatureName = 1*(ALPHA / DIGIT / "_" / "-")
//     *
//     * [USEFEATURE](https://torproject.gitlab.io/torspec/control-spec/#usefeature)
//     * */
//    public class UseFeature: Unprivileged<Unit>(Name.USEFEATURE)

    public enum class Name {
        AUTHENTICATE,
//        AUTHCHALLENGE,
//        CLOSECIRCUIT,
//        EXTENDCIRCUIT,
//        SETCIRCUITPURPOSE,
        GETCONF,
        LOADCONF,
        RESETCONF,
        SAVECONF,
        SETCONF,
        DROPGUARDS,
        HSFETCH,
//        HSPOST,
        GETINFO,
//        PROTOCOLINFO,
        MAPADDRESS,
        ADD_ONION,
        DEL_ONION,
        ONION_CLIENT_AUTH_ADD,
        ONION_CLIENT_AUTH_REMOVE,
        ONION_CLIENT_AUTH_VIEW,
        DROPOWNERSHIP,
        TAKEOWNERSHIP,
//        POSTDESCRIPTOR,
        RESOLVE,
        SETEVENTS,
        SIGNAL,
//        ATTACHSTREAM,
//        CLOSESTREAM,
//        REDIRECTSTREAM,
//        USEFEATURE
    }

    /**
     * A [TorCmd] whose use is restricted to only that of the control
     * connection, and not with TorRuntime.
     * */
    public sealed class Privileged<Response: Any>(name: Name): TorCmd<Response>(name) {

        /**
         * Base interface for implementations that process [Privileged] type [TorCmd]
         * */
        public interface Processor: Unprivileged.Processor {

            /**
             * Adds the [cmd] to the queue.
             *
             * **NOTE:** If the returned [TorJob] gets cancelled,
             * [onFailure] will be invoked with [CancellationException].
             *
             * @return [TorJob]
             * @see [enqueueAsync]
             * @throws [IOException] if tor has not been started.
             * */
            @Throws(IOException::class)
            public fun <Response: Any> enqueue(
                cmd: Privileged<Response>,
                onFailure: ItBlock<Throwable>?,
                onSuccess: ItBlock<Response>,
            ): TorJob
        }
    }

    /**
     * A [TorCmd] that is capable of being utilized with both the
     * control connection and TorRuntime (which passes it through to
     * the underlying control connection).
     * */
    public sealed class Unprivileged<Response: Any>(name: Name): TorCmd<Response>(name) {

        /**
         * Base interface for implementations that process [Unprivileged] type [TorCmd]
         * */
        public interface Processor {

            /**
             * Adds the [cmd] to the queue.
             *
             * **NOTE:** If the returned [TorJob] gets cancelled,
             * [onFailure] will be invoked with [CancellationException].
             *
             * @return [TorJob]
             * @see [enqueueAsync]
             * @throws [IOException] if tor has not been started.
             * */
            @Throws(IOException::class)
            public fun <Response: Any> enqueue(
                cmd: Unprivileged<Response>,
                onFailure: ItBlock<Throwable>?,
                onSuccess: ItBlock<Response>,
            ): TorJob
        }
    }

    public companion object {

        /**
         * Enqueues the [cmd], suspending the coroutine until completion
         * or cancellation/error.
         * */
        @JvmStatic
        @Throws(IOException::class, Throwable::class)
        public suspend fun <Response: Any> Privileged.Processor.enqueueAsync(
            cmd: Privileged<Response>,
        ): Response = suspendCancellableCoroutine { continuation ->
            var job: TorJob? = null

            try {
                job = enqueue(
                    cmd = cmd,
                    onFailure = { t ->
                        continuation.resumeWithException(t)
                    },
                    onSuccess = { result ->
                        @OptIn(ExperimentalCoroutinesApi::class)
                        continuation.resume(result, onCancellation = { t -> job?.cancel(t) })
                    },
                )
            } catch (e: IOException) {
                continuation.resumeWithException(e)
            }

            continuation.invokeOnCancellation { t -> job?.cancel(t) }
        }

        /**
         * Enqueues the [cmd], suspending the coroutine until completion
         * or cancellation/error.
         * */
        @JvmStatic
        @Throws(IOException::class, Throwable::class)
        public suspend fun <Response: Any> Unprivileged.Processor.enqueueAsync(
            cmd: Unprivileged<Response>,
        ): Response = suspendCancellableCoroutine { continuation ->
            var job: TorJob? = null

            try {
                job = enqueue(
                    cmd = cmd,
                    onFailure = { t ->
                        continuation.resumeWithException(t)
                    },
                    onSuccess = { result ->
                        @OptIn(ExperimentalCoroutinesApi::class)
                        continuation.resume(result, onCancellation = { t -> job?.cancel(t) })
                    },
                )
            } catch (e: IOException) {
                continuation.resumeWithException(e)
            }

            continuation.invokeOnCancellation { t -> job?.cancel(t) }
        }
    }

    final override fun toString(): String = "$name@${hashCode()}"
}
