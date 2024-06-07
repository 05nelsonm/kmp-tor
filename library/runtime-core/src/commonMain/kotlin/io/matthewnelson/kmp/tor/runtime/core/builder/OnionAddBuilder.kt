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
import io.matthewnelson.kmp.tor.runtime.core.ThisBlock
import io.matthewnelson.kmp.tor.runtime.core.TorConfig
import io.matthewnelson.kmp.tor.runtime.core.TorConfig.HiddenServiceMaxStreams
import io.matthewnelson.kmp.tor.runtime.core.TorConfig.HiddenServicePort
import io.matthewnelson.kmp.tor.runtime.core.apply
import io.matthewnelson.kmp.tor.runtime.core.ctrl.HiddenServiceEntry
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.core.key.AuthKey
import io.matthewnelson.kmp.tor.runtime.core.key.KeyType
import io.matthewnelson.kmp.tor.runtime.core.key.X25519
import kotlin.jvm.JvmField
import kotlin.jvm.JvmSynthetic

/**
 * Helper for configuring [TorCmd.Onion.Add] options.
 *
 * Tor's implementation of the ADD_ONION control command
 * is extremely overloaded with several "gotchas". This
 * attempts to alleviate those problems.
 *
 * **NOTE:** A minimum of 1 [port] **must** be declared for
 * the call to succeed (i.e. [HiddenServicePort.virtual] must
 * be set).
 *
 * e.g.
 *
 *     // Create new V3 HiddenService (tor will generate keys)
 *     val entry = runtime.executeAsync(TorCmd.Onion.Add(ED25519_V3) {
 *         port {
 *             virtual = 80.toPort()
 *             targetAsPort { target = 8080.toPort() }
 *         }
 *         port {
 *             virtual = 443.toPort()
 *             try {
 *                 targetAsUnixSocket {
 *                     file = runtime.environment()
 *                         .workDirectory
 *                         .resolve("test_hs.sock")
 *                 }
 *             } catch (_: UnsupportedOperationException) {
 *                 targetAsPort { target = 8443.toPort() }
 *             }
 *         }
 *         maxStreams { maximum = 25 }
 *     })
 *
 *     // Remove the HiddenService
 *     runtime.executeAsync(TorCmd.Onion.Delete(entry.publicKey))
 *
 *     // Re-add the HiddenService
 *     //
 *     // entry.privateKey will not be null because `DiscardPK`
 *     // flag was not defined when created above.
 *     runtime.executeAsync(TorCmd.Onion.Add(
 *         key = entry.privateKey!!,
 *         destroyKeyOnJobCompletion = true,
 *     ) {
 *         port {
 *             virtual = 80.toPort()
 *             targetAsPort { target = 8080.toPort() }
 *         }
 *         flags {
 *             DiscardPK = true
 *         }
 *     })
 *
 * @see [HiddenServiceEntry]
 * @see [TorConfig.HiddenServiceMaxStreams]
 * @see [TorConfig.HiddenServicePort]
 * */
@KmpTorDsl
public class OnionAddBuilder private constructor() {

    private val clientAuth = mutableSetOf<AuthKey.Public>()
    private val flags = mutableSetOf<String>()
    private var maxStreams: TorConfig.LineItem? = null
    private val ports = mutableSetOf<TorConfig.LineItem>()

    @KmpTorDsl
    public fun port(
        block: ThisBlock<HiddenServicePort>,
    ): OnionAddBuilder {
        val port = HiddenServicePort.build(block)
        if (port != null) ports.add(port)
        return this
    }

    @KmpTorDsl
    public fun clientAuth(
        key: AuthKey.Public,
    ): OnionAddBuilder {
        when (key) {
            is X25519.PublicKey -> "V3Auth"
        }.let { flags.add(it) }

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
//        @JvmField
//        public var V3Auth: Boolean? = null

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
//                b.V3Auth?.let {
//                    val flag = "V3Auth"
//                    if (it) flags.add(flag) else flags.remove(flag)
//                }
            }
        }
    }

    internal companion object {

        @JvmSynthetic
        internal fun KeyType.Address<*, *>.configure(
            block: ThisBlock<OnionAddBuilder>,
        ): Arguments {
            val b = OnionAddBuilder().apply(block)

            return Arguments(
                keyType = this,
                clientAuth = b.clientAuth,
                flags = b.flags,
                ports = b.ports,
                maxStreams = b.maxStreams,
            )
        }
    }

    internal class Arguments internal constructor(
        internal val keyType: KeyType.Address<*, *>,
        clientAuth: Set<AuthKey.Public>,
        flags: MutableSet<String>,
        ports: Set<TorConfig.LineItem>,
        internal val maxStreams: TorConfig.LineItem?,
    ) {

        internal val clientAuth = clientAuth.toImmutableSet()
        internal val flags = flags.toImmutableSet()
        internal val ports = ports.toImmutableSet()
    }
}
