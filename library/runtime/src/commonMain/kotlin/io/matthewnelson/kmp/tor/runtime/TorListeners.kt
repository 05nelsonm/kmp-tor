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
@file:Suppress("KotlinRedundantDiagnosticSuppress")

package io.matthewnelson.kmp.tor.runtime

import io.matthewnelson.immutable.collections.toImmutableSet
import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.resource.SynchronizedObject
import io.matthewnelson.kmp.tor.core.resource.synchronized
import io.matthewnelson.kmp.tor.runtime.FileID.Companion.fidEllipses
import io.matthewnelson.kmp.tor.runtime.core.address.IPSocketAddress
import io.matthewnelson.kmp.tor.runtime.core.address.IPSocketAddress.Companion.toIPSocketAddressOrNull
import io.matthewnelson.kmp.tor.runtime.internal.timedDelay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.jvm.JvmField
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmSynthetic
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Holder for [TorRuntime] listener addresses.
 *
 * @see [TorRuntime.listeners]
 * @see [RuntimeEvent.LISTENERS]
 * */
public class TorListeners private constructor(
    dns: Set<IPSocketAddress>,
    http: Set<IPSocketAddress>,
    socks: Set<IPSocketAddress>,
    socksUnix: Set<File>,
    trans: Set<IPSocketAddress>,
    private val fid: String?,
) {

    @JvmOverloads
    public constructor(
        dns: Set<IPSocketAddress> = emptySet(),
        http: Set<IPSocketAddress> = emptySet(),
        socks: Set<IPSocketAddress> = emptySet(),
        socksUnix: Set<File> = emptySet(),
        trans: Set<IPSocketAddress> = emptySet(),
    ): this(
        dns = dns,
        http = http,
        socks = socks,
        socksUnix = socksUnix,
        trans = trans,
        fid = null,
    )

    @JvmField
    public val dns: Set<IPSocketAddress> = dns.toImmutableSet()
    @JvmField
    public val http: Set<IPSocketAddress> = http.toImmutableSet()
    @JvmField
    public val socks: Set<IPSocketAddress> = socks.toImmutableSet()
    @JvmField
    public val socksUnix: Set<File> = socksUnix.toImmutableSet()
    @JvmField
    public val trans: Set<IPSocketAddress> = trans.toImmutableSet()

    /**
     * Helper to check if there are any listeners available for
     * all types (dns, http, socks, socksUnix, trans).
     *
     * @return true is no listeners are available for any types,
     *   false if there is at least 1 listener available.
     * */
    @get:JvmName("isEmpty")
    public val isEmpty: Boolean get() = dns.isEmpty()
        && http.isEmpty()
        && socks.isEmpty()
        && socksUnix.isEmpty()
        && trans.isEmpty()

    public operator fun component1(): Set<IPSocketAddress> = dns
    public operator fun component2(): Set<IPSocketAddress> = http
    public operator fun component3(): Set<IPSocketAddress> = socks
    public operator fun component4(): Set<File> = socksUnix
    public operator fun component5(): Set<IPSocketAddress> = trans

    // JvmOverloads is worthless here b/c of type erasure
    public fun copy(
        dns: Set<IPSocketAddress> = this.dns,
        http: Set<IPSocketAddress> = this.http,
        socks: Set<IPSocketAddress> = this.socks,
        socksUnix: Set<File> = this.socksUnix,
        trans: Set<IPSocketAddress> = this.trans,
    ): TorListeners {
        if (
            dns == this.dns
            && http == this.http
            && socks == this.socks
            && socksUnix == this.socksUnix
            && trans == this.trans
        ) {
            return this
        }

        return TorListeners(
            dns = dns,
            http = http,
            socks = socks,
            socksUnix = socksUnix,
            trans = trans,
            fid = fid,
        )
    }

    /**
     * Copies the current [TorListeners], replacing the
     * [TorListeners.dns] value with [dns].
     * */
    public fun copyDns(dns: Set<IPSocketAddress>): TorListeners = copy(dns = dns)

    /**
     * Copies the current [TorListeners], replacing the
     * [TorListeners.http] value with [http].
     * */
    public fun copyHttp(http: Set<IPSocketAddress>): TorListeners = copy(http = http)

    /**
     * Copies the current [TorListeners], replacing the
     * [TorListeners.socks] value with [socks].
     * */
    public fun copySocks(socks: Set<IPSocketAddress>): TorListeners = copy(socks = socks)

    /**
     * Copies the current [TorListeners], replacing the
     * [TorListeners.socksUnix] value with [socksUnix].
     * */
    public fun copySocksUnix(socksUnix: Set<File>): TorListeners = copy(socksUnix = socksUnix)

    /**
     * Copies the current [TorListeners], replacing the
     * [TorListeners.trans] value with [trans].
     * */
    public fun copyTrans(trans: Set<IPSocketAddress>): TorListeners = copy(trans = trans)

    internal companion object {

        @JvmSynthetic
        internal fun of(
            dns: Set<IPSocketAddress> = emptySet(),
            http: Set<IPSocketAddress> = emptySet(),
            socks: Set<IPSocketAddress> = emptySet(),
            socksUnix: Set<File> = emptySet(),
            trans: Set<IPSocketAddress> = emptySet(),
            fid: FileID?,
        ): TorListeners = TorListeners(
            dns = dns,
            http = http,
            socks = socks,
            socksUnix = socksUnix,
            trans = trans,
            fid = fid?.fidEllipses,
        )
    }

    public override fun equals(other: Any?): Boolean {
        return  other is TorListeners
                && other.dns == dns
                && other.http == http
                && other.socks == socks
                && other.socksUnix == socksUnix
                && other.trans == trans
    }

    public override fun hashCode(): Int {
        var result = 15
        result = result * 31 + dns.hashCode()
        result = result * 31 + http.hashCode()
        result = result * 31 + socks.hashCode()
        result = result * 31 + socksUnix.hashCode()
        result = result * 31 + trans.hashCode()
        return result
    }

    public override fun toString(): String = buildString {
        append("TorListeners")

        if (!fid.isNullOrBlank()) {
            append("[fid=")
            append(fid)
            append(']')
        }

        appendLine(": [")

        append("    dns: [")
        appendListeners(dns)

        append("    http: [")
        appendListeners(http)

        append("    socks: [")
        appendListeners(socks)

        append("    socksUnix: [")
        appendListeners(socksUnix)

        append("    trans: [")
        appendListeners(trans)

        append(']')
    }

    internal interface Manager: TorState.Manager {
        fun update(type: String, address: String, wasClosed: Boolean)
    }

    @OptIn(InternalKmpTorApi::class)
    internal abstract class AbstractManager internal constructor(
        private val scope: CoroutineScope,
        fid: FileID?,
        private val notifyDelay: Duration = 100.milliseconds
    ): TorState.AbstractManager(fid), Manager {

        @Suppress("PrivatePropertyName")
        private val EMPTY = of(fid = fid)

        @Volatile
        private var _listeners: TorListeners = EMPTY
        @Volatile
        private var _notifyJob: Job? = null
        private val lock = SynchronizedObject()

        internal val listeners: TorListeners get() = _listeners
        internal val listenersOrEmpty: TorListeners get() = with(state) {
            if (daemon.isBootstrapped && isNetworkEnabled) {
                _listeners
            } else {
                EMPTY
            }
        }

        protected abstract fun notify(listeners: TorListeners)
        protected abstract fun notify(state: TorState)

        override fun update(type: String, address: String, wasClosed: Boolean) {
            if (type.isBlank() || address.isBlank()) return
            val t = Type.valueOfOrNull(type) ?: return

            synchronized(lock) {
                val new = if (wasClosed) {
                    t.onClose(address)
                } else {
                    t.onOpen(address)
                }

                if (new == null || new == _listeners) {
                    return@synchronized
                }

                _listeners = new

                with(state) {
                    if (!daemon.isBootstrapped || isNetworkDisabled) {
                        return@synchronized
                    }
                }

                // Tor was running and there was a config change that
                // opened or closed listeners across multiple [notice]
                // lines.
                //
                // Wait for all of them come in before notifying.
                _notifyJob?.cancel()
                _notifyJob = scope.launch {
                    timedDelay(notifyDelay)
                    notify(new)
                }
            }
        }

        protected override fun notify(old: TorState, new: TorState) {
            val listeners = synchronized(lock) { with(_listeners) {
                // on -> NOT on
                if (old.isOn && !new.isOn) {
                    return@with EMPTY
                }

                if (new.daemon.isBootstrapped) {

                    // enabled -> disabled
                    if (old.isNetworkEnabled && new.isNetworkDisabled) {
                        return@with EMPTY
                    }

                    // disabled -> enabled
                    if (old.isNetworkDisabled && new.isNetworkEnabled) {
                        return@with this
                    }

                    // (NOT bootstrapped -> bootstrapped) + network
                    if (!old.daemon.isBootstrapped && new.isNetworkEnabled) {
                        return@with this
                    }
                }

                null
            }?.also {
                _notifyJob?.cancel()
                _listeners = it
            } }

            notify(new)
            listeners?.let { notify(it) }
        }

        private fun Type.onClose(address: String) = when (this) {
            is Type.DNS,
            is Type.HTTP,
            is Type.TRANSPARENT -> {
                address.toIPSocketAddressOrNull()
                    ?.onClose(this)
            }
            is Type.SOCKS -> when (address.firstOrNull()) {
                null -> null
                '?'-> {
                    // TODO: ConfChanged
                    null
                }
                '/' -> {
                    // TODO
                    null
                }
                else -> {
                    address.toIPSocketAddressOrNull()
                        ?.onClose(this)
                }
            }
        }

        private fun Type.onOpen(address: String) = when (this) {
            is Type.DNS,
            is Type.HTTP,
            is Type.TRANSPARENT -> {
                address.toIPSocketAddressOrNull()
                    ?.onOpen(this)
            }
            is Type.SOCKS -> when (address.firstOrNull()) {
                null -> null
                '/' -> {
                    // TODO
                    null
                }
                else -> {
                    address.toIPSocketAddressOrNull()
                        ?.onOpen(this)
                }
            }
        }

        private fun IPSocketAddress.onClose(
            type: Type
        ) = _listeners.update(type, this, wasClosed = true)

        private fun IPSocketAddress.onOpen(
            type: Type
        ) = _listeners.update(type, this, wasClosed = false)

        private fun TorListeners.update(
            type: Type,
            address: IPSocketAddress,
            wasClosed: Boolean,
        ) = when (type) {
            is Type.DNS -> dns to Copy.Address { copy(dns = it) }
            is Type.HTTP -> http to Copy.Address { copy(http = it) }
            is Type.SOCKS -> socks to Copy.Address { copy(socks = it) }
            is Type.TRANSPARENT -> trans to Copy.Address { copy(trans = it) }
        }.update(address, wasClosed)
    }

    private fun <T: Any> Pair<Set<T>, Copy<T>>.update(
        address: T,
        wasClosed: Boolean,
    ): TorListeners? {
        val (current, copy) = this

        val contains = current.contains(address)
        if (wasClosed && !contains) return null
        if (!wasClosed && contains) return null

        val mutable = current.toMutableSet()

        if (wasClosed) {
            mutable.remove(address)
        } else {
            mutable.add(address)
        }

        return copy.invoke(mutable)
    }

    private sealed interface Copy<T: Any> {
        fun invoke(new: MutableSet<T>): TorListeners

        fun interface Address: Copy<IPSocketAddress>
        fun interface Unix: Copy<File>
    }

    private sealed class Type {

        data object DNS: Type()
        data object HTTP: Type()
        data object SOCKS: Type()
        data object TRANSPARENT: Type()

        companion object {

            fun valueOfOrNull(name: String): Type? = when (name.uppercase()) {
                "DNS" -> DNS
                "HTTP" -> HTTP
                "SOCKS" -> SOCKS
                "TRANSPARENT" -> TRANSPARENT
                else -> null
            }
        }
    }
}

@Suppress("NOTHING_TO_INLINE")
private inline fun StringBuilder.appendListeners(set: Set<Any>): StringBuilder {
    if (set.isEmpty()) {
        appendLine(']')
        return this
    }

    for (element in set) {
        appendLine()
        append("        ")
        append(element)
    }

    appendLine()
    appendLine("    ]")
    return this
}
