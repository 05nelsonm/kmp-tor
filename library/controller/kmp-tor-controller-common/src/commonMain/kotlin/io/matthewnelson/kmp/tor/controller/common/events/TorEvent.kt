/*
 * Copyright (c) 2021 Matthew Nelson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
@file:Suppress("SpellCheckingInspection")

package io.matthewnelson.kmp.tor.controller.common.events

import kotlin.jvm.JvmStatic
import kotlin.native.concurrent.SharedImmutable

@SharedImmutable
private val SINGLE_LINE: Set<TorEvent.Type.SingleLineEvent> by lazy {
    val set: MutableSet<TorEvent.Type.SingleLineEvent> = LinkedHashSet(28)
    set.add(TorEvent.CircuitStatus)
    set.add(TorEvent.StreamStatus)
    set.add(TorEvent.ORConnStatus)
    set.add(TorEvent.BandwidthUsed)
    set.add(TorEvent.LogMsg.Debug)
    set.add(TorEvent.LogMsg.Info)
    set.add(TorEvent.LogMsg.Notice)
    set.add(TorEvent.LogMsg.Warn)
    set.add(TorEvent.LogMsg.Error)
    set.add(TorEvent.NewDesc)
    set.add(TorEvent.AddressMap)
    set.add(TorEvent.DescChanged)
    set.add(TorEvent.Status.General)
    set.add(TorEvent.Status.Client)
    set.add(TorEvent.Status.Server)
    set.add(TorEvent.Guard)
    set.add(TorEvent.StreamBandwidthUsed)
    set.add(TorEvent.ClientsSeen)
    set.add(TorEvent.BuildTimeoutSet)
    set.add(TorEvent.SignalReceived)
    set.add(TorEvent.ConfChanged)
    set.add(TorEvent.CircuitStatusMinor)
    set.add(TorEvent.TransportLaunched)
    set.add(TorEvent.ConnBandwidth)
    set.add(TorEvent.CircuitBandwidthUsed)
    set.add(TorEvent.CellStats)
    set.add(TorEvent.HSDescriptor)
    set.add(TorEvent.NetworkLiveness)
    set
}

@SharedImmutable
private val MULTI_LINE: Set<TorEvent.Type.MultiLineEvent> by lazy {
    val set: MutableSet<TorEvent.Type.MultiLineEvent> = LinkedHashSet(3)
    set.add(TorEvent.NetworkStatus)
    set.add(TorEvent.NewConsensus)
    set.add(TorEvent.HSDescriptorContent)
    set
}

/**
 * https://torproject.gitlab.io/torspec/control-spec/#asynchronous-events
 * */
sealed class TorEvent private constructor(val value: String) {

    companion object {
        @get:JvmStatic
        val SINGLE_LINE_EVENTS: Set<Type.SingleLineEvent> get() = SINGLE_LINE
        @get:JvmStatic
        val MULTI_LINE_EVENTS: Set<Type.MultiLineEvent> get() = MULTI_LINE
    }

    override fun toString(): String {
        return value
    }

    fun compareTo(other: String?): Boolean =
        other != null && other == value

    /**
     * Tor has 2 types of responses for variaous events; single and multi-line.
     *
     * The [Type] inherited by all [TorEvent]s denotes which is which when parsing
     * replies off the control port.
     *
     * @see [SealedListener]
     * @see [Listener]
     * */
    sealed class Type private constructor(value: String): TorEvent(value) {
        sealed class SingleLineEvent(value: String): Type(value)
        sealed class MultiLineEvent(value: String): Type(value)
    }

    /**
     * https://torproject.gitlab.io/torspec/control-spec/#circuit-status-changed
     * */
    object CircuitStatus                : Type.SingleLineEvent("CIRC")

    /**
     * https://torproject.gitlab.io/torspec/control-spec/#stream-status-changed
     * */
    object StreamStatus                 : Type.SingleLineEvent("STREAM")

    /**
     * https://torproject.gitlab.io/torspec/control-spec/#or-connection-status-changed
     * */
    object ORConnStatus                 : Type.SingleLineEvent("ORCONN")

    /**
     * https://torproject.gitlab.io/torspec/control-spec/#bandwidth-used-in-the-last-second
     * */
    object BandwidthUsed                : Type.SingleLineEvent("BW")

    /**
     * https://torproject.gitlab.io/torspec/control-spec/#log-messages
     * */
    sealed class LogMsg(value: String)  : Type.SingleLineEvent(value) {
        object Debug                        : LogMsg("DEBUG")
        object Info                         : LogMsg("INFO")
        object Notice                       : LogMsg("NOTICE")
        object Warn                         : LogMsg("WARN")
        object Error                        : LogMsg("ERR")
    }

    /**
     * https://torproject.gitlab.io/torspec/control-spec/#new-descriptors-available
     * */
    object NewDesc                      : Type.SingleLineEvent("NEWDESC")

    /**
     * https://torproject.gitlab.io/torspec/control-spec/#new-address-mapping
     * */
    object AddressMap                   : Type.SingleLineEvent("ADDRMAP")

    /**
     * https://torproject.gitlab.io/torspec/control-spec/#our-descriptor-changed
     * */
    object DescChanged                  : Type.SingleLineEvent("DESCCHANGED")

    /**
     * https://torproject.gitlab.io/torspec/control-spec/#status-events
     * */
    sealed class Status(value: String)  : Type.SingleLineEvent("STATUS_$value") {
        object General                      : Status("GENERAL")
        object Client                       : Status("CLIENT")
        object Server                       : Status("SERVER")
    }

    /**
     * https://torproject.gitlab.io/torspec/control-spec/#our-set-of-guard-nodes-has-changed
     * */
    object Guard                        : Type.SingleLineEvent("GUARD")

    /**
     * https://torproject.gitlab.io/torspec/control-spec/#network-status-has-changed
     * */
    object NetworkStatus                : Type.MultiLineEvent("NS")

    /**
     * https://torproject.gitlab.io/torspec/control-spec/#bandwidth-used-on-an-application-stream
     * */
    object StreamBandwidthUsed          : Type.SingleLineEvent("STREAM_BW")

    /**
     * https://torproject.gitlab.io/torspec/control-spec/#per-country-client-stats
     * */
    object ClientsSeen                  : Type.SingleLineEvent("CLIENTS_SEEN")

    /**
     * https://torproject.gitlab.io/torspec/control-spec/#new-consensus-networkstatus-has-arrived
     * */
    object NewConsensus                 : Type.MultiLineEvent("NEWCONSENSUS")

    /**
     * https://torproject.gitlab.io/torspec/control-spec/#new-circuit-buildtime-has-been-set
     * */
    object BuildTimeoutSet              : Type.SingleLineEvent("BUILDTIMEOUT_SET")

    /**
     * https://torproject.gitlab.io/torspec/control-spec/#signal-received
     * */
    object SignalReceived               : Type.SingleLineEvent("SIGNAL")

    /**
     * https://torproject.gitlab.io/torspec/control-spec/#configuration-changed
     * */
    object ConfChanged                  : Type.SingleLineEvent("CONF_CHANGED")

    /**
     * https://torproject.gitlab.io/torspec/control-spec/#circuit-status-changed-slightly
     * */
    object CircuitStatusMinor           : Type.SingleLineEvent("CIRC_MINOR")

    /**
     * https://torproject.gitlab.io/torspec/control-spec/#pluggable-transport-launched
     * */
    object TransportLaunched            : Type.SingleLineEvent("TRANSPORT_LAUNCHED")

    /**
     * https://torproject.gitlab.io/torspec/control-spec/#bandwidth-used-on-an-or-or-dir-or-exit-connection
     * */
    object ConnBandwidth                : Type.SingleLineEvent("CONN_BW")

    /**
     * https://torproject.gitlab.io/torspec/control-spec/#bandwidth-used-by-all-streams-attached-to-a-circuit
     * */
    object CircuitBandwidthUsed         : Type.SingleLineEvent("CIRC_BW")

    /**
     * https://torproject.gitlab.io/torspec/control-spec/#per-circuit-cell-stats
     * */
    object CellStats                    : Type.SingleLineEvent("CELL_STATS")

    /**
     * https://torproject.gitlab.io/torspec/control-spec/#hiddenservice-descriptors
     * */
    object HSDescriptor                 : Type.SingleLineEvent("HS_DESC")

    /**
     * https://torproject.gitlab.io/torspec/control-spec/#hiddenservice-descriptors-content
     * */
    object HSDescriptorContent          : Type.MultiLineEvent("HS_DESC_CONTENT")

    /**
     * https://torproject.gitlab.io/torspec/control-spec/#network-liveness-has-changed
     * */
    object NetworkLiveness              : Type.SingleLineEvent("NETWORK_LIVENESS")

//    sealed class PluggableTransport(value: String)  : Type.SingleLineEvent("PT_$value") {
//
//        /**
//         * https://torproject.gitlab.io/torspec/control-spec/#pluggable-transport-logs
//         * */
//        object Log                                      : PluggableTransport("LOG")
//
//        /**
//         * https://torproject.gitlab.io/torspec/control-spec/#pluggable-transport-status
//         * */
//        object Status                                   : PluggableTransport("STATUS")
//    }

    /**
     * There is some nuance here in terms of what thread [onEvent] is
     * dispatched on, depending on where it is coming from.
     *
     * TorController dispatches events on a background thread to all of its
     * listeners. In the event you are using TorManager, the manager will
     * attach a single listener (itself) to the TorController upon start up
     * and as events come in, they will then be dispatched to all listeners
     * attached to TorManager via the Main thread.
     *
     * In summary:
     *  - [onEvent] from TorController attached listener = background thread
     *  - [onEvent] from TorManager attached listener = Main thread
     * */
    interface SealedListener {
        fun onEvent(event: Type.SingleLineEvent, output: String)
        fun onEvent(event: Type.MultiLineEvent, output: List<String>)
    }

    /**
     * Prefer using [Listener] over [SealedListener] when possible, as future
     * additions to [TorEvent] sealed classes can produce errors which would
     * otherwise be handled for you here with the addition of another open method
     * to override upon updating.
     * */
    abstract class Listener: SealedListener {
        open fun eventHSDescriptorContent(output: List<String>) {}
        open fun eventNetworkStatus(output: List<String>) {}
        open fun eventNewConsensus(output: List<String>) {}

        override fun onEvent(event: Type.MultiLineEvent, output: List<String>) {
            when (event) {
                HSDescriptorContent -> eventHSDescriptorContent(output)
                NetworkStatus -> eventNetworkStatus(output)
                NewConsensus -> eventNewConsensus(output)
            }
        }

        open fun eventAddressMap(output: String) {}
        open fun eventBandwidthUsed(output: String) {}
        open fun eventBuildTimeoutSet(output: String) {}
        open fun eventCellStats(output: String) {}
        open fun eventCircuitBandwidthUsed(output: String) {}
        open fun eventCircuitStatus(output: String) {}
        open fun eventCircuitStatusMinor(output: String) {}
        open fun eventClientsSeen(output: String) {}
        open fun eventConfChanged(output: String) {}
        open fun eventConnBandwidth(output: String) {}
        open fun eventDescChanged(output: String) {}
        open fun eventGuard(output: String) {}
        open fun eventHSDescriptor(output: String) {}
        open fun eventLogDebug(output: String) {}
        open fun eventLogError(output: String) {}
        open fun eventLogInfo(output: String) {}
        open fun eventLogNotice(output: String) {}
        open fun eventLogWarn(output: String) {}
        open fun eventNetworkLiveness(output: String) {}
        open fun eventNewDesc(output: String) {}
        open fun eventORConnStatus(output: String) {}
        open fun eventSignalReceived(output: String) {}
        open fun eventStatusClient(output: String) {}
        open fun eventStatusGeneral(output: String) {}
        open fun eventStatusServer(output: String) {}
        open fun eventStreamBandwidthUsed(output: String) {}
        open fun eventStreamStatus(output: String) {}
        open fun eventTransportLaunched(output: String) {}

        override fun onEvent(event: Type.SingleLineEvent, output: String) {
            when (event) {
                AddressMap -> eventAddressMap(output)
                BandwidthUsed -> eventBandwidthUsed(output)
                BuildTimeoutSet -> eventBuildTimeoutSet(output)
                CellStats -> eventCellStats(output)
                CircuitBandwidthUsed -> eventCircuitBandwidthUsed(output)
                CircuitStatus -> eventCircuitStatus(output)
                CircuitStatusMinor -> eventCircuitStatusMinor(output)
                ClientsSeen -> eventClientsSeen(output)
                ConfChanged -> eventConfChanged(output)
                ConnBandwidth -> eventConnBandwidth(output)
                DescChanged -> eventDescChanged(output)
                Guard -> eventGuard(output)
                HSDescriptor -> eventHSDescriptor(output)
                LogMsg.Debug -> eventLogDebug(output)
                LogMsg.Error -> eventLogError(output)
                LogMsg.Info -> eventLogInfo(output)
                LogMsg.Notice -> eventLogNotice(output)
                LogMsg.Warn -> eventLogWarn(output)
                NetworkLiveness -> eventNetworkLiveness(output)
                NewDesc -> eventNewDesc(output)
                ORConnStatus -> eventORConnStatus(output)
                SignalReceived -> eventSignalReceived(output)
                Status.Client -> eventStatusClient(output)
                Status.General -> eventStatusGeneral(output)
                Status.Server -> eventStatusServer(output)
                StreamBandwidthUsed -> eventStreamBandwidthUsed(output)
                StreamStatus -> eventStreamStatus(output)
                TransportLaunched -> eventTransportLaunched(output)
            }
        }
    }

}
