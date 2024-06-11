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
@file:Suppress("SpellCheckingInspection", "ClassName")

package io.matthewnelson.kmp.tor.runtime.core

import kotlin.jvm.JvmStatic

/**
 * [Asynchronous Events](https://torproject.gitlab.io/torspec/control-spec/#asynchronous-events)
 *
 * e.g.
 *
 *     val circObserver = TorEvent.CIRC.observer { data ->
 *         println(data)
 *     }
 *
 * @see [Observer]
 * @see [Event.observer]
 * @see [Processor]
 * @see [TorEvent.Companion]
 * @see [valueOf]
 * @see [valueOfOrNull]
 * @see [entries]
 * */
public sealed class TorEvent private constructor(
    name: String,
): Event<String, TorEvent, TorEvent.Observer>(name) {

    /**
     * [Circuit Status Changed](https://torproject.gitlab.io/torspec/control-spec/#circuit-status-changed)
     * */
    public data object CIRC: TorEvent("CIRC")

    /**
     * [Stream Status Changed](https://torproject.gitlab.io/torspec/control-spec/#stream-status-changed)
     * */
    public data object STREAM: TorEvent("STREAM")

    /**
     * [OR Connection Status Changed](https://torproject.gitlab.io/torspec/control-spec/#or-connection-status-changed)
     * */
    public data object ORCONN: TorEvent("ORCONN")

    /**
     * [Bandwidth Used In The Last Second](https://torproject.gitlab.io/torspec/control-spec/#bandwidth-used-in-the-last-second)
     * */
    public data object BW: TorEvent("BW")

    /**
     * [Log Messages](https://torproject.gitlab.io/torspec/control-spec/#log-messages)
     * */
    public data object DEBUG: TorEvent("DEBUG")
    /**
     * [Log Messages](https://torproject.gitlab.io/torspec/control-spec/#log-messages)
     * */
    public data object INFO: TorEvent("INFO")
    /**
     * [Log Messages](https://torproject.gitlab.io/torspec/control-spec/#log-messages)
     * */
    public data object NOTICE: TorEvent("NOTICE")
    /**
     * [Log Messages](https://torproject.gitlab.io/torspec/control-spec/#log-messages)
     * */
    public data object WARN: TorEvent("WARN")
    /**
     * [Log Messages](https://torproject.gitlab.io/torspec/control-spec/#log-messages)
     * */
    public data object ERR: TorEvent("ERR")

    /**
     * [New Descriptors Available](https://torproject.gitlab.io/torspec/control-spec/#new-descriptors-available)
     * */
    public data object NEWDESC: TorEvent("NEWDESC")

    /**
     * [New Address Mapping](https://torproject.gitlab.io/torspec/control-spec/#new-address-mapping)
     * */
    public data object ADDRMAP: TorEvent("ADDRMAP")

    /**
     * [Our Descriptor Changed](https://torproject.gitlab.io/torspec/control-spec/#our-descriptor-changed)
     * */
    public data object DESCCHANGED: TorEvent("DESCCHANGED")

    /**
     * [Status Events](https://torproject.gitlab.io/torspec/control-spec/#status-events)
     * */
    public data object STATUS_GENERAL: TorEvent("STATUS_GENERAL")
    /**
     * [Status Events](https://torproject.gitlab.io/torspec/control-spec/#status-events)
     * */
    public data object STATUS_CLIENT: TorEvent("STATUS_CLIENT")
    /**
     * [Status Events](https://torproject.gitlab.io/torspec/control-spec/#status-events)
     * */
    public data object STATUS_SERVER: TorEvent("STATUS_SERVER")

    /**
     * [Our Set Of Guard Nodes Changed](https://torproject.gitlab.io/torspec/control-spec/#our-set-of-guard-nodes-has-changed)
     * */
    public data object GUARD: TorEvent("GUARD")

    /**
     * [Network Status Changed](https://torproject.gitlab.io/torspec/control-spec/#network-status-has-changed)
     * */
    public data object NS: TorEvent("NS")

    /**
     * [Bandwidth Used On An Application Stream](https://torproject.gitlab.io/torspec/control-spec/#bandwidth-used-on-an-application-stream)
     * */
    public data object STREAM_BW: TorEvent("STREAM_BW")

    /**
     * [Per-country Client Stats](https://torproject.gitlab.io/torspec/control-spec/#per-country-client-stats)
     * */
    public data object CLIENTS_SEEN: TorEvent("CLIENTS_SEEN")

    /**
     * [New Consensus Network Status Arrived](https://torproject.gitlab.io/torspec/control-spec/#new-consensus-networkstatus-has-arrived)
     * */
    public data object NEWCONSENSUS: TorEvent("NEWCONSENSUS")

    /**
     * [New Circuit Build Time Has Been Set](https://torproject.gitlab.io/torspec/control-spec/#new-circuit-buildtime-has-been-set)
     * */
    public data object BUILDTIMEOUT_SET: TorEvent("BUILDTIMEOUT_SET")

    /**
     * [Signal Received](https://torproject.gitlab.io/torspec/control-spec/#signal-received)
     * */
    public data object SIGNAL: TorEvent("SIGNAL")

    /**
     * [Configuration Changed](https://torproject.gitlab.io/torspec/control-spec/#configuration-changed)
     * */
    public data object CONF_CHANGED: TorEvent("CONF_CHANGED")

    /**
     * [Circuit Status Changed Slightly](https://torproject.gitlab.io/torspec/control-spec/#circuit-status-changed-slightly)
     * */
    public data object CIRC_MINOR: TorEvent("CIRC_MINOR")

    /**
     * [Pluggable Transport Launched](https://torproject.gitlab.io/torspec/control-spec/#pluggable-transport-launched)
     * */
    public data object TRANSPORT_LAUNCHED: TorEvent("TRANSPORT_LAUNCHED")

    /**
     * [Bandwidth Used On An OR Dir Or Exit Connection](https://torproject.gitlab.io/torspec/control-spec/#bandwidth-used-on-an-or-or-dir-or-exit-connection)
     * */
    public data object CONN_BW: TorEvent("CONN_BW")

    /**
     * [Bandwidth Used By All Streams Attached To A Circuit](https://torproject.gitlab.io/torspec/control-spec/#bandwidth-used-by-all-streams-attached-to-a-circuit)
     * */
    public data object CIRC_BW: TorEvent("CIRC_BW")

    /**
     * [Per-circuit Cell Stats](https://torproject.gitlab.io/torspec/control-spec/#per-circuit-cell-stats)
     * */
    public data object CELL_STATS: TorEvent("CELL_STATS")

    /**
     * [HiddenService Descriptors](https://torproject.gitlab.io/torspec/control-spec/#hiddenservice-descriptors)
     * */
    public data object HS_DESC: TorEvent("HS_DESC")

    /**
     * [HiddenService Descriptors Content](https://torproject.gitlab.io/torspec/control-spec/#hiddenservice-descriptors-content)
     * */
    public data object HS_DESC_CONTENT: TorEvent("HS_DESC_CONTENT")

    /**
     * [Network Liveness Changed](https://torproject.gitlab.io/torspec/control-spec/#network-liveness-has-changed)
     * */
    public data object NETWORK_LIVENESS: TorEvent("NETWORK_LIVENESS")

//    /**
//     * [Pluggable Transport Logs](https://torproject.gitlab.io/torspec/control-spec/#pluggable-transport-logs)
//     * */
//    public data object PT_LOG: TorEvent("PT_LOG")
//
//    /**
//     * [Pluggable Transport Status](https://torproject.gitlab.io/torspec/control-spec/#pluggable-transport-status)
//     * */
//    public data object PT_STATUS: TorEvent("PT_STATUS")

    /**
     * Model to be registered with a [Processor] for being notified
     * via [OnEvent] invocation with [TorEvent] data.
     *
     * @see [Event.Observer]
     * @see [Processor]
     * */
    public open class Observer(
        event: TorEvent,
        tag: String?,
        executor: OnEvent.Executor?,
        onEvent: OnEvent<String>,
    ): Event.Observer<String, TorEvent>(
        event,
        tag,
        executor,
        onEvent,
    )

    /**
     * Base interface for implementations that process [TorEvent].
     * */
    public interface Processor {

        /**
         * Add a single [Observer].
         * */
        public fun subscribe(observer: Observer)

        /**
         * Add multiple [Observer].
         * */
        public fun subscribe(vararg observers: Observer)

        /**
         * Remove a single [Observer].
         * */
        public fun unsubscribe(observer: Observer)

        /**
         * Remove multiple [Observer].
         * */
        public fun unsubscribe(vararg observers: Observer)

        /**
         * Remove all [Observer] of a single [TorEvent].
         * */
        public fun unsubscribeAll(event: TorEvent)

        /**
         * Remove all [Observer] of multiple [TorEvent].
         * */
        public fun unsubscribeAll(vararg events: TorEvent)

        /**
         * Remove all [Observer] with the given [tag].
         *
         * If the implementin class extends both [Processor]
         * and [io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Processor],
         * all [io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Observer]
         * with the given [tag] will also be removed.
         * */
        public fun unsubscribeAll(tag: String)

        /**
         * Remove all non-static [Observer] that are currently
         * registered.
         *
         * If the implementin class extends both [Processor]
         * and [io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Processor],
         * all [io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Observer]
         * will also be removed.
         * */
        public fun clearObservers()
    }

    public companion object: Entries<TorEvent>(numEvents = 31) {

        @JvmStatic
        @Throws(IllegalArgumentException::class)
        public override fun valueOf(name: String): TorEvent {
            return super.valueOf(name)
        }

        @JvmStatic
        public override fun valueOfOrNull(name: String): TorEvent? {
            return super.valueOfOrNull(name)
        }

        @JvmStatic
        public override fun entries(): Set<TorEvent> {
            return super.entries()
        }

        protected override val lazyEntries: ThisBlock<LinkedHashSet<TorEvent>> = ThisBlock {
            // NOTE: Update numEvents when adding an event
            add(CIRC); add(STREAM); add(ORCONN); add(BW);
            add(DEBUG); add(INFO); add(NOTICE); add(WARN);
            add(ERR); add(NEWDESC); add(ADDRMAP); add(DESCCHANGED);
            add(STATUS_GENERAL); add(STATUS_CLIENT); add(STATUS_SERVER); add(GUARD);
            add(NS); add(STREAM_BW); add(CLIENTS_SEEN); add(NEWCONSENSUS);
            add(BUILDTIMEOUT_SET); add(SIGNAL); add(CONF_CHANGED); add(CIRC_MINOR);
            add(TRANSPORT_LAUNCHED); add(CONN_BW); add(CIRC_BW); add(CELL_STATS);
            add(HS_DESC); add(HS_DESC_CONTENT); add(NETWORK_LIVENESS);
        }
    }

    protected final override fun factory(
        event: TorEvent,
        tag: String?,
        executor: OnEvent.Executor?,
        onEvent: OnEvent<String>,
    ): Observer = Observer(event, tag, executor, onEvent)
}
