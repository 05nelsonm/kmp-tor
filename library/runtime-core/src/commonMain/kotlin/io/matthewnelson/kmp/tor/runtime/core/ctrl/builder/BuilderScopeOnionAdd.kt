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
@file:Suppress("PropertyName", "UnusedReceiverParameter")

package io.matthewnelson.kmp.tor.runtime.core.ctrl.builder

import io.matthewnelson.immutable.collections.toImmutableSet
import io.matthewnelson.kmp.tor.core.api.annotation.KmpTorDsl
import io.matthewnelson.kmp.tor.runtime.core.EnqueuedJob
import io.matthewnelson.kmp.tor.runtime.core.ThisBlock
import io.matthewnelson.kmp.tor.runtime.core.net.Port
import io.matthewnelson.kmp.tor.runtime.core.apply
import io.matthewnelson.kmp.tor.runtime.core.config.TorSetting
import io.matthewnelson.kmp.tor.runtime.core.config.builder.BuilderScopeHSPort
import io.matthewnelson.kmp.tor.runtime.core.config.builder.BuilderScopeHSPort.Companion.configureHSPort
import io.matthewnelson.kmp.tor.runtime.core.ctrl.HiddenServiceEntry
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.core.internal.configure
import io.matthewnelson.kmp.tor.runtime.core.key.*
import kotlin.jvm.JvmField
import kotlin.jvm.JvmSynthetic

/**
 * A DSL builder scope for configuring [TorCmd.Onion.Add]. The `ONION_ADD`
 * control command has an extremely overloaded API. This attempts to
 * alleviate some of its pain points and peculiarities by splitting things
 * into 2 "modes" of operation; `new` and `existing`.
 *
 * **NOTE:** Both "modes" require a minimum of 1 [port] to be defined.
 * Otherwise, the call will fail.
 *
 * ## NEW [TorCmd.Onion.Add.new]:
 *
 * Creates a command object that will instruct tor to generate keys for, and
 * add to its runtime, a new Hidden Service.
 *
 * e.g. (Generating a new Hidden Service)
 *
 *     TorCmd.Onion.Add.new(type = ED25519_V3) {
 *         port(virtual = Port.HTTP) {
 *             target(port = 8080.toPort())
 *         }
 *     }
 *
 * **NOTE:** After call completion, the returned [HiddenServiceEntry.privateKey]
 * should be destroyed when done with it!
 *
 * ## EXISTING [TorCmd.Onion.Add.existing]:
 *
 * Creates a command object that will instruct tor to add an existing Hidden
 * Service to its runtime, for the provided [AddressKey.Private].
 *
 * e.g. (Adding an existing Hidden Service you have keys for)
 *
 *     TorCmd.Onion.Add.existing(key = "[Blob Redacted]".toED25519_V3PrivateKey()) {
 *         port(virtual = Port.HTTP) {
 *             target(port = 8080.toPort())
 *         }
 *     }
 *
 * **NOTE:** [FlagsBuilder.DiscardPK] is automatically added for this "mode" in
 * order to mitigate unnecessary private key material exposure. It can be disabled
 * by explicitly setting the flag option to `false`.
 *
 * **NOTE:** [destroyKeyOnJobCompletion] is automatically set to `true` for this
 * "mode" in order to mitigate unnecessary private key material exposure. It can
 * be disabled by explicitly setting it to `false.
 *
 * ## EXAMPLE:
 *
 * e.g. (A full blown example using `kmp-tor:runtime`)
 *
 *     // Create new V3 Hidden Service (tor will generate keys)
 *     val entry = runtime.executeAsync(TorCmd.Onion.Add.new(ED25519_V3) {
 *         port(virtual = Port.HTTP) {
 *             target(port = 8080.toPort())
 *         }
 *         port(virtual = Port.HTTPS) {
 *             try {
 *                 target(unixSocket = runtime.environment()
 *                     .workDirectory
 *                     .resolve("test_hs.sock"))
 *             } catch (_: UnsupportedOperationException) {
 *                 // Fallback to TCP if on system w/o
 *                 // UnixSocket support.
 *                 target(port = 8443.toPort())
 *             }
 *         }
 *         maxStreams(25)
 *     })
 *
 *     // Remove the Hidden Service
 *     runtime.executeAsync(TorCmd.Onion.Delete(entry.publicKey))
 *
 *     // Re-add the Hidden Service
 *     //
 *     // entry.privateKey will not be `null` because `DiscardPK`
 *     // flag was not defined when created above.
 *     val newEntry = runtime.executeAsync(TorCmd.Onion.Add.existing(entry.privateKey!!) {
 *         port(virtual = Port.HTTP) {
 *             target(port = 8080.toPort())
 *         }
 *     })
 *
 *     // `destroyKeyOnJobCompletion` was `true` (the default) which
 *     // cleaned things up on call completion.
 *     assertTrue(entry.privateKey!!.isDestroyed())
 *
 *     // The `DiscardPK` flag was automatically added for `existing`
 *     // "mode" and not changed above, so tor did not return one.
 *     assertNull(newEntry.privateKey)
 *
 * @see [TorCmd.Onion.Add]
 * @see [HiddenServiceEntry]
 * */
@KmpTorDsl
public class BuilderScopeOnionAdd private constructor(
    private val keyType: KeyType.Address<*, *>
): BuilderScopeHSPort.DSL<BuilderScopeOnionAdd> {

    // Required to be defined (minimum of 1)
    private val _ports = LinkedHashSet<TorSetting.LineItem>(1, 1.0f)

    // Tor defaults if undefined
    private var _maxStreams: Int? = null
    private var _destroyKeyOnJobCompletion: Boolean = true
    private val _clientAuth = LinkedHashSet<AuthKey.Public>(1, 1.0f)
    private val _flags = LinkedHashSet<String>(1, 1.0f)

    /**
     * When true, an [EnqueuedJob.invokeOnCompletion] handler is automatically
     * set when the resulting [TorCmd.Onion.Add] object is enqueued. The handler
     * calls [AddressKey.Private.destroy] upon job completion (either successfully
     * or by cancellation/error).
     *
     * **NOTE:** This setting has no effect when [TorCmd.Onion.Add.new] is being
     * utilized, as there will be no [AddressKey.Private] to destroy.
     *
     * Default: `true`
     * */
    @KmpTorDsl
    public fun destroyKeyOnJobCompletion(
        destroy: Boolean,
    ): BuilderScopeOnionAdd {
        _destroyKeyOnJobCompletion = destroy
        return this
    }

    // See BuilderScopeHSPort.DSL interface
    @KmpTorDsl
    public override fun port(
        virtual: Port,
    ): BuilderScopeOnionAdd = port(virtual) {}

    // See BuilderScopeHSPort.DSL interface
    @KmpTorDsl
    public override fun port(
        virtual: Port,
        block: ThisBlock<BuilderScopeHSPort>,
    ): BuilderScopeOnionAdd = configureHSPort(virtual, _ports, block)

    /**
     * Add [AuthKey.Public] for client authentication.
     * */
    @KmpTorDsl
    public fun clientAuth(
        key: X25519.PublicKey,
    ): BuilderScopeOnionAdd {
        // TODO: Should define errors or something
        //  so they can be thrown when the cmd object
        //  gets enqueued...
        if (keyType !is ED25519_V3) return this
        _flags.add("V3Auth")
        _clientAuth.add(key)
        return this
    }

    /**
     * Add and/or remove flags.
     *
     * @see [FlagsBuilder]
     * */
    @KmpTorDsl
    public fun flags(
        block: ThisBlock<FlagsBuilder>,
    ): BuilderScopeOnionAdd {
        FlagsBuilder.configure(_flags, block)
        return this
    }

    /**
     * Sets the `MaxStreams` argument for [TorCmd.Onion.Add].
     *
     * **NOTE:** Must be between [Port.MIN] and [Port.MAX] (inclusive).
     * Otherwise, the call will fail.
     * */
    @KmpTorDsl
    public fun maxStreams(
        num: Int,
    ): BuilderScopeOnionAdd {
        _maxStreams = num
        return this
    }

    /**
     * Configure flags for the [TorCmd.Onion.Add] object, as described in
     * [control-spec#ADD_ONION](https://spec.torproject.org/control-spec/commands.html#add_onion).
     *
     * **NOTE:** Flag `V3Auth` is not included here as an option. It
     * is automatically added if [clientAuth] is configured.
     *
     * Configurability is as follows:
     *  - `null`: no action (default).
     *  - `true`: add the flag if not present.
     *  - `false`: remove the flag if present.
     *
     * e.g.
     *
     *     flags {
     *         Detach = true
     *         DiscardPK = true
     *     }
     *     flags {
     *         // Remove what was just added
     *         Detach = false
     *     }
     *
     *     // ...
     *
     *     println(onionAddCmd.flags)
     *     // [DiscardPK]
     *
     * */
    @KmpTorDsl
    public class FlagsBuilder private constructor() {

        @JvmField
        public var Detach: Boolean? = null
        @JvmField
        public var DiscardPK: Boolean? = null
        @JvmField
        public var MaxStreamsCloseCircuit: Boolean? = null
//        @JvmField
//        public var NonAnonymous: Boolean? = null

        internal companion object {

            @JvmSynthetic
            internal fun configure(
                flags: LinkedHashSet<String>,
                block: ThisBlock<FlagsBuilder>,
            ) {
                val b = FlagsBuilder().apply(block)

                b.Detach.configure(flags, "Detach")
                b.DiscardPK.configure(flags, "DiscardPK")
                b.MaxStreamsCloseCircuit.configure(flags, "MaxStreamsCloseCircuit")
//                b.NonAnonymous.configure(flags, "NonAnonymous")
            }
        }
    }

    internal companion object {

        @JvmSynthetic
        internal fun KeyType.Address<*, *>.configure(
            isExisting: Boolean,
            block: ThisBlock<BuilderScopeOnionAdd>,
        ): Arguments {
            val b = BuilderScopeOnionAdd(this)

            if (isExisting) {
                b.flags { DiscardPK = true }
            }

            b.apply(block)

            return Arguments(
                clientAuth = b._clientAuth,
                destroyKeyOnJobCompletion = b._destroyKeyOnJobCompletion,
                flags = b._flags,
                keyType = this,
                maxStreams = b._maxStreams,
                ports = b._ports,
            )
        }
    }

    internal class Arguments internal constructor(
        clientAuth: Set<AuthKey.Public>,
        internal val destroyKeyOnJobCompletion: Boolean,
        flags: Set<String>,
        internal val keyType: KeyType.Address<*, *>,
        internal val maxStreams: Int?,
        ports: Set<TorSetting.LineItem>,
    ) {

        internal val clientAuth = clientAuth.toImmutableSet()
        internal val flags = flags.toImmutableSet()
        internal val ports = ports.toImmutableSet()
    }
}
