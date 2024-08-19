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

package io.matthewnelson.kmp.tor.runtime.core.builder

import io.matthewnelson.immutable.collections.toImmutableSet
import io.matthewnelson.kmp.tor.core.api.annotation.KmpTorDsl
import io.matthewnelson.kmp.tor.runtime.core.EnqueuedJob
import io.matthewnelson.kmp.tor.runtime.core.ThisBlock
import io.matthewnelson.kmp.tor.runtime.core.TorConfig
import io.matthewnelson.kmp.tor.runtime.core.TorConfig.HiddenServiceMaxStreams
import io.matthewnelson.kmp.tor.runtime.core.TorConfig.HiddenServicePort
import io.matthewnelson.kmp.tor.runtime.core.address.Port
import io.matthewnelson.kmp.tor.runtime.core.apply
import io.matthewnelson.kmp.tor.runtime.core.ctrl.HiddenServiceEntry
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.core.key.*
import kotlin.jvm.JvmField
import kotlin.jvm.JvmSynthetic

/**
 * Helper for configuring [TorCmd.Onion.Add] options.
 *
 * Tor's implementation of the ADD_ONION control command
 * is extremely overloaded with several "gotchas". This
 * attempts to alleviate some pain points.
 *
 * **NOTE:** A minimum of 1 [port] **must** be declared for
 * the call to succeed (i.e. [HiddenServicePort.virtual] must
 * be set).
 *
 * e.g.
 *
 *     // Create new V3 Hidden Service (tor will generate keys)
 *     val entry = runtime.executeAsync(TorCmd.Onion.Add(ED25519_V3) {
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
 *         maxStreams { maximum = 25 }
 *     })
 *
 *     // Remove the Hidden Service
 *     runtime.executeAsync(TorCmd.Onion.Delete(entry.publicKey))
 *
 *     // Re-add the Hidden Service
 *     //
 *     // entry.privateKey will not be `null` because `DiscardPK`
 *     // flag was not defined when created above.
 *     val newEntry = runtime.executeAsync(TorCmd.Onion.Add(entry.privateKey!!) {
 *         port(virtual = Port.HTTP) {
 *             target(port = 8080.toPort())
 *         }
 *     })
 *
 *     // destroyKeyOnJobCompletion was true (the default) and cleaned
 *     // up private key material automatically for re-adding.
 *     assertTrue(entry.privateKey!!.isDestroyed())
 *
 *     // The `DiscardPK` flag was automatically set for adding existing
 *     // Hidden Service, so tor did not return a private key.
 *     assertNull(newEntry.privateKey)
 *
 * @see [TorCmd.Onion.Add]
 * @see [HiddenServiceEntry]
 * @see [TorConfig.HiddenServiceMaxStreams]
 * @see [TorConfig.HiddenServicePort]
 * */
@KmpTorDsl
public class OnionAddBuilder private constructor(private val keyType: KeyType.Address<*, *>) {

    private val clientAuth = LinkedHashSet<AuthKey.Public>(1, 1.0f)
    private val flags = LinkedHashSet<String>(1, 1.0f)
    private var maxStreams: TorConfig.LineItem? = null
    private val ports = LinkedHashSet<TorConfig.LineItem>(1, 1.0f)

    /**
     * When true, an [EnqueuedJob.invokeOnCompletion] handler is
     * automatically set which calls [AddressKey.Private.destroy]
     * once the job completes, either successfully or by
     * cancellation/error.
     *
     * This setting has no effect if [TorCmd.Onion.Add] is being
     * instantiated using [KeyType.Address] for new Hidden Service
     * creation, as there will be no [AddressKey.Private] present.
     *
     * Default: `true`
     * */
    @JvmField
    public var destroyKeyOnJobCompletion: Boolean = true

    @KmpTorDsl
    public fun port(
        virtual: Port,
    ): OnionAddBuilder = port(virtual) {}

    @KmpTorDsl
    public fun port(
        virtual: Port,
        block: ThisBlock<HiddenServicePort>,
    ): OnionAddBuilder {
        val port = HiddenServicePort.build(virtual, block)
        ports.add(port)
        return this
    }

    @KmpTorDsl
    public fun clientAuth(
        key: X25519.PublicKey,
    ): OnionAddBuilder {
        if (keyType !is ED25519_V3) return this
        flags.add("V3Auth")
        clientAuth.add(key)
        return this
    }

    @KmpTorDsl
    public fun flags(
        block: ThisBlock<FlagBuilder>,
    ): OnionAddBuilder {
        FlagBuilder.configure(flags, block)
        return this
    }

    @KmpTorDsl
    public fun maxStreams(
        block: ThisBlock<HiddenServiceMaxStreams>,
    ): OnionAddBuilder {
        maxStreams = HiddenServiceMaxStreams.build(block)
        return this
    }

    /**
     * Configure flags specific to [TorCmd.Onion.Add].
     *
     * - `null`  - no action (default)
     * - `true`  - add the flag if not present
     * - `false` - remove the flag if present
     * */
    @KmpTorDsl
    public class FlagBuilder private constructor() {

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
                flags: MutableSet<String>,
                block: ThisBlock<FlagBuilder>,
            ) {
                val b = FlagBuilder().apply(block)

                b.Detach?.let {
                    val flag = "Detach"
                    if (it) flags.add(flag) else flags.remove(flag)
                }
                b.DiscardPK?.let {
                    val flag = "DiscardPK"
                    if (it) flags.add(flag) else flags.remove(flag)
                }
                b.MaxStreamsCloseCircuit?.let {
                    val flag = "MaxStreamsCloseCircuit"
                    if (it) flags.add(flag) else flags.remove(flag)
                }
//                b.NonAnonymous?.let {
//                    val flag = "NonAnonymous"
//                    if (it) flags.add(flag) else flags.remove(flag)
//                }
            }
        }
    }

    internal companion object {

        @JvmSynthetic
        internal fun KeyType.Address<*, *>.configure(
            isExisting: Boolean,
            block: ThisBlock<OnionAddBuilder>,
        ): Arguments {
            val b = OnionAddBuilder(this)

            if (isExisting) {
                b.flags { DiscardPK = true }
            }

            b.apply(block)

            return Arguments(
                clientAuth = b.clientAuth,
                destroyKeyOnJobCompletion = b.destroyKeyOnJobCompletion,
                flags = b.flags,
                keyType = this,
                maxStreams = b.maxStreams,
                ports = b.ports,
            )
        }
    }

    internal class Arguments internal constructor(
        clientAuth: Set<AuthKey.Public>,
        internal val destroyKeyOnJobCompletion: Boolean,
        flags: Set<String>,
        internal val keyType: KeyType.Address<*, *>,
        internal val maxStreams: TorConfig.LineItem?,
        ports: Set<TorConfig.LineItem>,
    ) {

        internal val clientAuth = clientAuth.toImmutableSet()
        internal val flags = flags.toImmutableSet()
        internal val ports = ports.toImmutableSet()
    }
}
