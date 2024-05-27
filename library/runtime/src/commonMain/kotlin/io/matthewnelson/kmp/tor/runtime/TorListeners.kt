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
import io.matthewnelson.kmp.file.path
import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.resource.SynchronizedObject
import io.matthewnelson.kmp.tor.core.resource.synchronized
import io.matthewnelson.kmp.tor.runtime.FileID.Companion.fidEllipses
import io.matthewnelson.kmp.tor.runtime.core.*
import io.matthewnelson.kmp.tor.runtime.core.TorConfig.Setting.Companion.filterByKeyword
import io.matthewnelson.kmp.tor.runtime.core.address.IPSocketAddress
import io.matthewnelson.kmp.tor.runtime.core.address.IPSocketAddress.Companion.toIPSocketAddressOrNull
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.ctrl.TorCmdInterceptor
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
        fun onListenerConfChange(type: String, changes: Set<String>)
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

        internal val interceptorConfigReset = TorCmdInterceptor.intercept<TorCmd.Config.Reset> { job, cmd ->
            onConfigChangeJob(cmd, job)
            cmd
        }
        internal val interceptorConfigSet = TorCmdInterceptor.intercept<TorCmd.Config.Set> { job, cmd ->
            onConfigChangeJob(cmd, job)
            cmd
        }

        protected abstract fun notify(listeners: TorListeners)
        protected abstract fun notify(state: TorState)

        final override fun onListenerConfChange(type: String, changes: Set<String>) {
            val executable = when (Type.valueOfOrNull(type)) {
                is Type.SOCKS -> Executable {
                    Type.SOCKS.diffUnixListeners(_listeners.socksUnix, changes)
                }
                else -> return
            }

            synchronized(lock) { executable.execute() }
        }

        final override fun update(type: String, address: String, wasClosed: Boolean) {
            if (address.isBlank()) return
            val t = Type.valueOfOrNull(type) ?: return
            synchronized(lock) { updateNoLock(t, address, wasClosed) }
        }

        private fun updateNoLock(type: Type, address: String, wasClosed: Boolean) {
            val new = if (wasClosed) {
                type.onClose(address)
            } else {
                type.onOpen(address)
            }

            if (new == null || new == _listeners) {
                return
            }

            _listeners = new

            with(state) {
                if (!daemon.isBootstrapped || isNetworkDisabled) {
                    return
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

                    if (new.isNetworkEnabled) {

                        // disabled -> enabled
                        if (old.isNetworkDisabled) {
                            return@with this
                        }

                        // NOT bootstrapped -> bootstrapped
                        if (!old.daemon.isBootstrapped) {
                            return@with this
                        }
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
                    ?.update(type = this, wasClosed = true)
            }
            is Type.SOCKS -> when (address.firstOrNull()) {
                null -> null
                '?'-> { // ???:0
                    // tor dispatched a NOTICE indicating that it was closing a
                    // unix listener.
                    //
                    // If it is attributed to the network being disabled, nothing
                    // to worry about b/c it will be cleared when the state change
                    // comes through in just a moment.
                    //
                    // If it is attributed to a CONF_CHANGED event containing SocksPort
                    // modifications, that will be dispatched **JUST** after this one
                    // and diffUnixListeners will handle it.
                    null
                }
                '/' ->  with(_listeners) {
                    (socksUnix to Copy.Unix { copy(socksUnix = it) })
                        .update(address.toFile(), wasClosed = true)
                }
                else -> {
                    address.toIPSocketAddressOrNull()
                        ?.update(type = this, wasClosed = true)
                }
            }
        }

        private fun Type.onOpen(address: String) = when (this) {
            is Type.DNS,
            is Type.HTTP,
            is Type.TRANSPARENT -> {
                address.toIPSocketAddressOrNull()
                    ?.update(type = this, wasClosed = false)
            }
            is Type.SOCKS -> when (address.firstOrNull()) {
                null -> null
                '/' -> with(_listeners) {
                    (socksUnix to Copy.Unix { copy(socksUnix = it) })
                        .update(address.toFile(), wasClosed = false)
                }
                else -> {
                    address.toIPSocketAddressOrNull()
                        ?.update(type = this, wasClosed = false)
                }
            }
        }

        private fun IPSocketAddress.update(
            type: Type,
            wasClosed: Boolean,
        ) = _listeners.update(type, address = this, wasClosed)

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

        protected open fun onConfigChangeJob(cmd: TorCmd.Config.Reset, job: EnqueuedJob) {
            job.invokeOnErrorRecovery(recovery = {
                if (cmd.keywords.contains(TorConfig.__SocksPort)) {
                    // SocksPort was set to default. tor will still
                    // close all other socks listeners that may be
                    // open, but will not dispatch the CONF_CHANGED
                    // event.
                    onListenerConfChange("Socks", setOf("9050"))
                }
            })
        }

        protected open fun onConfigChangeJob(cmd: TorCmd.Config.Set, job: EnqueuedJob) {
            job.invokeOnErrorRecovery(recovery = {
                val changes = cmd.settings
                    .filterByKeyword<TorConfig.__SocksPort.Companion>()
                    .takeIf { it.isNotEmpty() }
                    ?.let { settings ->
                        settings.mapTo(LinkedHashSet(settings.size, 1.0F)) { setting ->
                            setting.toString().substringAfter(' ')
                        }
                    }

                if (changes != null) {
                    onListenerConfChange("Socks", changes)
                }
            })
        }

        private fun EnqueuedJob.invokeOnErrorRecovery(recovery: Executable) {
            invokeOnCompletion {
                // CONF_CHANGE event will be dispatched.
                if (isSuccess) return@invokeOnCompletion

                // There was an error. Tor will not dispatch a CONF_CHANGED
                // even, but it will still close other listeners that may be
                // open.

                // No need to go further if there are no unix listeners open
                // that need diffing. Any that may have been opened by this
                // failed job will be "partially-constructed" and tor dispatches
                // a NOTICE closing them which contains the full file path and
                // occurs before replying with the Reply.Error for this job.
                val isRecoveryNeeded = with(listeners) {
                    socksUnix.isNotEmpty()
                }

                if (isRecoveryNeeded) {
                    recovery.execute()
                }
            }
        }

        private fun Type.diffUnixListeners(listeners: Set<File>, changes: Set<String>) {
            when (this) {
                Type.SOCKS -> {}
                else -> return
            }

            with(state) {
                if (!(isOn || isStarting)) return
                if (isNetworkDisabled) return
            }
            if (listeners.isEmpty()) return

            val diffs = LinkedHashMap<File, Boolean>(listeners.size, 1.0F)

            listeners.forEach { file ->
                var wasClosed = true

                for (change in changes) {
                    // Change could be a TCP port or socket address, a
                    // quoted or unquoted path prefixed with "unix:", contain
                    // optionals appended to it, etc.
                    //
                    // The simplest way to check which unix listeners have
                    // been closed is to see if any of the changes contain
                    // the absolute path string. If it does, then it was NOT
                    // closed.
                    if (change.contains(file.path)) {
                        wasClosed = false
                        break
                    }
                }

                diffs[file] = wasClosed
            }

            diffs.forEach { (file, wasClosed) -> updateNoLock(type = this, file.path, wasClosed) }
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
