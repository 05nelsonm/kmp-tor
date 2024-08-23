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

package io.matthewnelson.kmp.tor.runtime.core.ctrl.builder

import io.matthewnelson.immutable.collections.toImmutableSet
import io.matthewnelson.kmp.tor.core.api.annotation.KmpTorDsl
import io.matthewnelson.kmp.tor.runtime.core.EnqueuedJob
import io.matthewnelson.kmp.tor.runtime.core.ThisBlock
import io.matthewnelson.kmp.tor.runtime.core.apply
import io.matthewnelson.kmp.tor.runtime.core.config.TorConfig
import io.matthewnelson.kmp.tor.runtime.core.config.TorOption
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.core.internal.configure
import io.matthewnelson.kmp.tor.runtime.core.key.AuthKey
import kotlin.jvm.JvmField
import kotlin.jvm.JvmSynthetic

/**
 * A DSL builder scope for configuring [TorCmd.OnionClientAuth.Add].
 *
 * All elements of this builder scope are optional, and not required.
 * */
@KmpTorDsl
public class BuilderScopeClientAuthAdd private constructor() {

    private var _clientName: String? = null
    private var _destroyKeyOnJobCompletion: Boolean = true
    private val _flags = LinkedHashSet<String>(1, 1.0f)

    /**
     * Specify the [nickname] for this client key.
     *
     * **NOTE:** Cannot exceed 16 characters in length or contain
     * whitespace. Otherwise, the call will fail.
     * */
    @KmpTorDsl
    public fun clientName(
        nickname: String?,
    ): BuilderScopeClientAuthAdd {
        _clientName = nickname
        return this
    }

    /**
     * When true, an [EnqueuedJob.invokeOnCompletion] handler is automatically
     * set when the resulting [TorCmd.OnionClientAuth.Add] object is enqueued.
     * The handler calls [AuthKey.Private.destroy] upon job completion (either
     * successfully or by cancellation/error).
     *
     * Default: `true`
     * */
    @KmpTorDsl
    public fun destroyKeyOnJobCompletion(
        destroy: Boolean,
    ): BuilderScopeClientAuthAdd {
        _destroyKeyOnJobCompletion = destroy
        return this
    }

    /**
     * Add and/or remove flags.
     * */
    @KmpTorDsl
    public fun flags(
        block: ThisBlock<FlagsBuilder>,
    ): BuilderScopeClientAuthAdd {
        FlagsBuilder.configure(_flags, block)
        return this
    }

    /**
     * Configure flags for the [TorCmd.OnionClientAuth.Add] object, as described
     * in  [control-spec#ONION_CLIENT_AUTH_ADD](https://spec.torproject.org/control-spec/commands.html#onion_client_auth_add).
     *
     * Configurability is as follows:
     *  - `null`: no action (default).
     *  - `true`: add the flag if not present.
     *  - `false`: remove the flag if present.
     * */
    @KmpTorDsl
    public class FlagsBuilder private constructor() {

        /**
         * **NOTE:** Adding this flag requires that [TorOption.ClientOnionAuthDir]
         * be defined in your [TorConfig]. Otherwise, the call will fail.
         * */
        @JvmField
        public var Permanent: Boolean? = null

        internal companion object {

            @JvmSynthetic
            internal fun configure(
                flags: LinkedHashSet<String>,
                block: ThisBlock<FlagsBuilder>,
            ) {
                val b = FlagsBuilder().apply(block)

                b.Permanent.configure(flags, "Permanent")
            }
        }
    }

    internal companion object {

        @JvmSynthetic
        internal fun configure(
            block: ThisBlock<BuilderScopeClientAuthAdd>,
        ): Arguments {
            val b = BuilderScopeClientAuthAdd().apply(block)

            return Arguments.of(
                clientName = b._clientName,
                destroyKeyOnJobCompletion = b._destroyKeyOnJobCompletion,
                flags = b._flags,
            )
        }
    }

    internal class Arguments private constructor(
        internal val clientName: String?,
        internal val destroyKeyOnJobCompletion: Boolean,
        flags: Set<String>,
    ) {

        internal val flags = flags.toImmutableSet()

        internal companion object {

            internal val DEFAULT = Arguments(null, true, emptySet())

            @JvmSynthetic
            internal fun of(
                clientName: String?,
                destroyKeyOnJobCompletion: Boolean,
                flags: Set<String>,
            ): Arguments {
                if (
                    clientName == DEFAULT.clientName
                    && destroyKeyOnJobCompletion == DEFAULT.destroyKeyOnJobCompletion
                    && flags == DEFAULT.flags
                ) {
                    return DEFAULT
                }

                return Arguments(
                    clientName,
                    destroyKeyOnJobCompletion,
                    flags,
                )
            }
        }
    }
}
