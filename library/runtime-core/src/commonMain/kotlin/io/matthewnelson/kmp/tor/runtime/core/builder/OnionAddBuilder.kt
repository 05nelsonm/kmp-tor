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
import io.matthewnelson.kmp.tor.runtime.core.key.AddressKey
import io.matthewnelson.kmp.tor.runtime.core.key.KeyType
import kotlin.jvm.JvmField
import kotlin.jvm.JvmSynthetic

@KmpTorDsl
public sealed class OnionAddBuilder<T: OnionAddBuilder<T>> private constructor() {

    private var maxStreams: TorConfig.LineItem? = null
    private val ports = mutableSetOf<TorConfig.LineItem>()

    @KmpTorDsl
    public fun port(
        block: ThisBlock<HiddenServicePort>,
    ): T {
        val port = HiddenServicePort.build(block)
        if (port != null) ports.add(port)
        return This
    }

    @KmpTorDsl
    public fun maxStreams(
        block: ThisBlock<HiddenServiceMaxStreams>,
    ): T {
        maxStreams = HiddenServiceMaxStreams.build(block)
        return This
    }

//    @JvmField
//    public var DiscardPK: Boolean = false
    @JvmField
    public var Detach: Boolean = false
    @JvmField
    public var NonAnonymous: Boolean = false
    @JvmField
    public var MaxStreamsCloseCircuit: Boolean = false
//    @JvmField
//    public var V3Auth: Boolean = false

    @KmpTorDsl
    public class Existing private constructor(): OnionAddBuilder<Existing>() {

        @JvmField
        public var destroyKeyOnJobCompletion: Boolean = true

        internal companion object {

            @JvmSynthetic
            internal fun AddressKey.Private.configure(
                block: ThisBlock<Existing>,
            ): Arguments = Existing().apply(block).toArguments()
        }
    }

    @KmpTorDsl
    public class New private constructor(): OnionAddBuilder<New>() {

        internal companion object {

            @JvmSynthetic
            internal fun KeyType.Address<*, *>.configure(
                block: ThisBlock<New>,
            ): Arguments = New().apply(block).toArguments()
        }
    }

    internal class Arguments internal constructor(
        flags: Set<String>,
        ports: Set<TorConfig.LineItem>,
        internal val destroyKey: Boolean,
        internal val maxStreams: TorConfig.LineItem?,
    ) {

        internal val flags = flags.toImmutableSet()
        internal val ports = ports.toImmutableSet()
    }

    @Suppress("NOTHING_TO_INLINE", "PrivatePropertyName", "UNCHECKED_CAST", "KotlinRedundantDiagnosticSuppress")
    private inline val This: T get() = this as T

    protected companion object {

        internal fun OnionAddBuilder<*>.toArguments(): Arguments {
            val flags = mutableSetOf<String>()

            if (Detach) flags.add("Detach")
            if (NonAnonymous) flags.add("NonAnonymous")
            if (MaxStreamsCloseCircuit) flags.add("MaxStreamsCloseCircuit")

            val destroy = if (this is Existing) {
                destroyKeyOnJobCompletion
            } else {
                false
            }

            return Arguments(flags, ports, destroy, maxStreams)
        }
    }
}
