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
@file:Suppress("PropertyName")

package io.matthewnelson.kmp.tor.runtime.core.builder

import io.matthewnelson.immutable.collections.toImmutableSet
import io.matthewnelson.kmp.tor.core.api.annotation.KmpTorDsl
import io.matthewnelson.kmp.tor.runtime.core.EnqueuedJob
import io.matthewnelson.kmp.tor.runtime.core.ThisBlock
import io.matthewnelson.kmp.tor.runtime.core.apply
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.core.key.AuthKey
import kotlin.jvm.JvmField
import kotlin.jvm.JvmSynthetic

@KmpTorDsl
public class OnionClientAuthAddBuilder private constructor() {

    private val flags = LinkedHashSet<String>(1, 1.0f)

    /**
     * Cannot exceed 16 characters in length and must not contain
     * whitespace, otherwise tor will reject it.
     * */
    @JvmField
    public var clientName: String? = null

    /**
     * When true, an [EnqueuedJob.invokeOnCompletion] handler is
     * automatically set which calls [AuthKey.Private.destroy]
     * once the job completes, either successfully or by
     * cancellation/error.
     *
     * Default = `true`
     * */
    @JvmField
    public var destroyKeyOnJobCompletion: Boolean = true

    @KmpTorDsl
    public fun flags(
        block: ThisBlock<FlagBuilder>,
    ): OnionClientAuthAddBuilder {
        FlagBuilder.configure(flags, block)
        return this
    }

    /**
     * Configure flags specific to [TorCmd.OnionClientAuth.Add].
     *
     * - `null`  - no action (default)
     * - `true`  - add the flag if not present
     * - `false` - remove the flag if present
     * */
    @KmpTorDsl
    public class FlagBuilder private constructor() {

        @JvmField
        public var Permanent: Boolean? = null

        internal companion object {

            @JvmSynthetic
            internal fun configure(
                flags: MutableSet<String>,
                block: ThisBlock<FlagBuilder>,
            ) {
                val b = FlagBuilder().apply(block)

                b.Permanent?.let {
                    val flag = "Permanent"
                    if (it) flags.add(flag) else flags.remove(flag)
                }
            }
        }
    }

    internal companion object {

        @JvmSynthetic
        internal fun configure(
            block: ThisBlock<OnionClientAuthAddBuilder>,
        ): Arguments {
            val b = OnionClientAuthAddBuilder().apply(block)

            return Arguments(
                clientName = b.clientName,
                destroyKeyOnJobCompletion = b.destroyKeyOnJobCompletion,
                flags = b.flags,
            )
        }
    }

    internal class Arguments internal constructor(
        internal val clientName: String?,
        internal val destroyKeyOnJobCompletion: Boolean,
        flags: Set<String>,
    ) {

        internal val flags = flags.toImmutableSet()

        internal companion object {

            internal val EMPTY = Arguments(null, true, emptySet())
        }
    }
}
