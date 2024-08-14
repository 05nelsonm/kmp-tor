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
@file:Suppress("JoinDeclarationAndAssignment")

package io.matthewnelson.kmp.tor.runtime.core.ctrl

import io.matthewnelson.kmp.tor.runtime.core.address.IPAddress
import io.matthewnelson.kmp.tor.runtime.core.address.OnionAddress
import kotlin.jvm.JvmField
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * Holder for address mapping requests.
 *
 * e.g.
 *
 *     val results = runtime.executeAsync(
 *         TorCmd.MapAddress(
 *             "torproject.org".mappingToAnyHost(),
 *             "torproject.org".mappingToAnyHostIPv4(),
 *             "torproject.org".mappingToAnyHostIPv6(),
 *         )
 *     )
 *
 *     val unmappings = results.map { result ->
 *         println(result)
 *         result.toUnmapping()
 *     }
 *
 *     // AddressMapping.Result: [
 *     //     from: wsvidzeicnyrlruo.virtual
 *     //     to: torproject.org
 *     //     isUnmapping: false
 *     // ]
 *     // AddressMapping.Result: [
 *     //     from: 127.240.73.168
 *     //     to: torproject.org
 *     //     isUnmapping: false
 *     // ]
 *     // AddressMapping.Result: [
 *     //     from: [fe85:2bcd:7057:b3f9:ffb2:be48:1e7b:884]
 *     //     to: torproject.org
 *     //     isUnmapping: false
 *     // ]
 *
 *     runtime.executeAsync(
 *         TorCmd.Info.Get("address-mappings/control")
 *     ).let { mappings -> println(mappings.values.first()) }
 *
 *     // wsvidzeicnyrlruo.virtual torproject.org NEVER
 *     // 127.240.73.168 torproject.org NEVER
 *     // [fe85:2bcd:7057:b3f9:ffb2:be48:1e7b:884] torproject.org NEVER
 *
 *     runtime.executeAsync(TorCmd.MapAddress(unmappings))
 *
 *     runtime.executeAsync(
 *         TorCmd.Info.Get("address-mappings/control")
 *     ).let { mappings -> println(mappings) }
 *
 *     // {address-mappings/control=}
 *
 * @see [TorCmd.MapAddress]
 * @see [AddressMapping.Result]
 * */
public class AddressMapping(from: String, to: String) {

    public constructor(from: String, to: IPAddress): this(from, to.canonicalHostName())
    public constructor(from: IPAddress, to: String): this(from.canonicalHostName(), to)
    public constructor(from: IPAddress, to: IPAddress): this(from.canonicalHostName(), to.canonicalHostName())
    public constructor(from: IPAddress, to: OnionAddress): this(from.canonicalHostName(), to.canonicalHostName())

    /**
     * The "original", or "old", address that will be mapped to [to].
     * */
    @JvmField
    public val from: String

    /**
     * The "replacement", or "new", address that [from] will be mapped to.
     * */
    @JvmField
    public val to: String

    public operator fun component1(): String = from
    public operator fun component2(): String = to

    @JvmOverloads
    public fun copy(
        from: String = this.from,
        to: String = this.to,
    ): AddressMapping {
        if (from == this.from && to == this.to) return this
        return AddressMapping(from, to)
    }

    public companion object {

        /**
         * Creates a [AddressMapping] that instructs tor to generate
         * a random host value (e.g. `4lr2xdqckbl4nttj.virtual`) and
         * map the provided string (host name) to it.
         *
         * If the string (host name) is already mapped, tor will return
         * that mapping for [AddressMapping.Result].
         * */
        @JvmStatic
        @JvmName("anyHostTo")
        public fun String.mappingToAnyHost(): AddressMapping {
            return AddressMapping(".", this)
        }

        /**
         * Creates a [AddressMapping] that instructs tor to generate
         * a virtual [IPAddress.V4] address and map the provided string
         * (host name) to it.
         *
         * If the string (host name) is already mapped, tor will return
         * that mapping for [AddressMapping.Result].
         * */
        @JvmStatic
        @JvmName("anyHostIPv4To")
        public fun String.mappingToAnyHostIPv4(): AddressMapping {
            return AddressMapping(IPAddress.V4.AnyHost, this)
        }

        /**
         * Creates a [AddressMapping] that instructs tor to generate
         * a virtual [IPAddress.V4] address and map the provided
         * [OnionAddress] to it.
         *
         * If the [OnionAddress] is already mapped, tor will return
         * that mapping for [AddressMapping.Result].
         * */
        @JvmStatic
        @JvmName("anyHostIPv4To")
        public fun OnionAddress.mappingToAnyHostIPv4(): AddressMapping {
            return AddressMapping(IPAddress.V4.AnyHost, this)
        }

        /**
         * Creates a [AddressMapping] that instructs tor to generate
         * a virtual [IPAddress.V6] address and map the provided string
         * (host name) to it.
         *
         * If the string (host name) is already mapped, tor will return
         * that mapping for [AddressMapping.Result].
         * */
        @JvmStatic
        @JvmName("anyHostIPv6To")
        public fun String.mappingToAnyHostIPv6(): AddressMapping {
            return AddressMapping(IPAddress.V6.AnyHost.NoScope, this)
        }

        /**
         * Creates a [AddressMapping] that instructs tor to generate
         * a virtual [IPAddress.V4] address and map the provided
         * [OnionAddress] to it.
         *
         * If the [OnionAddress] is already mapped, tor will return
         * that mapping for [AddressMapping.Result].
         * */
        @JvmStatic
        @JvmName("anyHostIPv6To")
        public fun OnionAddress.mappingToAnyHostIPv6(): AddressMapping {
            return AddressMapping(IPAddress.V6.AnyHost.NoScope, this)
        }

        /**
         * Creates a [AddressMapping] that instruct tor to unmap any
         * addresses associated with the provided string (host name).
         * */
        @JvmStatic
        @JvmName("unmapFrom")
        public fun String.unmappingFrom(): AddressMapping {
            return AddressMapping(this, this)
        }

        /**
         * Creates a [AddressMapping] that instruct tor to unmap any
         * addresses associated with the provided [IPAddress].
         * */
        @JvmStatic
        @JvmName("unmapFrom")
        public fun IPAddress.unmappingFrom(): AddressMapping {
            return AddressMapping(this, this)
        }
    }

    /**
     * Holder for response from [TorCmd.MapAddress]
     * */
    public class Result(

        /**
         * The "original", or "old", address that
         * has been mapped to [to].
         * */
        @JvmField
        public val from: String,

        /**
         * The "replacement", or "new", address that
         * [from] has been mapped to.
         * */
        @JvmField
        public val to: String,
    ) {

        /**
         * Indicates that this [Result] was an "unmapping"
         * of the address (i.e. tor removed the mapping from
         * its indices).
         *
         * @see [unmappingFrom]
         * */
        @JvmField
        public val isUnmapping: Boolean = from == to

        /**
         * Creates a new [AddressMapping] request using [from].
         *
         * @see [unmappingFrom]
         * */
        public fun toUnmapping(): AddressMapping = from.unmappingFrom()

        /** @suppress */
        public override fun equals(other: Any?): Boolean {
            return  other is Result
                    && other.from == from
                    && other.to == to
        }

        /** @suppress */
        public override fun hashCode(): Int {
            var result = 17
            result = result * 42 + from.hashCode()
            result = result * 42 + to.hashCode()
            return result
        }

        /** @suppress */
        public override fun toString(): String = buildString {
            appendLine("AddressMapping.Result: [")
            append("    from: ")
            appendLine(from)
            append("    to: ")
            appendLine(to)
            append("    isUnmapping: ")
            appendLine(isUnmapping)
            append(']')
        }
    }

    init {
        this.from = when (from) {
            // For mapping to IPv6 any host, tor expects
            // unbracketed `::` and nothing else.
            //
            // Because constructor allows for IPAddress,
            // and the implementation for IPAddress.V6
            // **always** expands addresses to 8 blocks,
            // this swaps it out for the expected `::`
            // any host value.
            "::",
            "::0",
            IPAddress.V6.AnyHost.value,
            "[::]",
            "[::0]",
            IPAddress.V6.AnyHost.canonicalHostName() -> "::"
            else -> from
        }

        this.to = to
    }

    /** @suppress */
    public override fun equals(other: Any?): Boolean {
        return  other is AddressMapping
                && other.from == from
                && other.to == to
    }

    /** @suppress */
    public override fun hashCode(): Int {
        var result = 15
        result = result * 42 + from.hashCode()
        result = result * 42 + to.hashCode()
        return result
    }

    /** @suppress */
    public override fun toString(): String {
        return "AddressMapping[from=$from, to=$to]"
    }
}
