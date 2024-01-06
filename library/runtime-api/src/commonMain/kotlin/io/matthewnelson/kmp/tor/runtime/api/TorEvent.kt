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
@file:Suppress("SpellCheckingInspection")

package io.matthewnelson.kmp.tor.runtime.api

import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.resource.SynchronizedObject
import io.matthewnelson.kmp.tor.core.resource.synchronized
import kotlin.jvm.JvmField

/**
 * [Asynchronous Events](https://torproject.gitlab.io/torspec/control-spec/#asynchronous-events)
 *
 * @see [Observer]
 * @see [observer]
 * @see [Processor]
 * */
public enum class TorEvent {

    /**
     * [Circuit Status Changed](https://torproject.gitlab.io/torspec/control-spec/#circuit-status-changed)
     * */
    CIRC,

    /**
     * [Stream Status Changed](https://torproject.gitlab.io/torspec/control-spec/#stream-status-changed)
     * */
    STREAM,

    /**
     * [OR Connection Status Changed](https://torproject.gitlab.io/torspec/control-spec/#or-connection-status-changed)
     * */
    ORCONN,

    /**
     * [Bandwidth Used In The Last Second](https://torproject.gitlab.io/torspec/control-spec/#bandwidth-used-in-the-last-second)
     * */
    BW,

    /**
     * [Log Messages](https://torproject.gitlab.io/torspec/control-spec/#log-messages)
     * */
    DEBUG,
    /**
     * [Log Messages](https://torproject.gitlab.io/torspec/control-spec/#log-messages)
     * */
    INFO,
    /**
     * [Log Messages](https://torproject.gitlab.io/torspec/control-spec/#log-messages)
     * */
    NOTICE,
    /**
     * [Log Messages](https://torproject.gitlab.io/torspec/control-spec/#log-messages)
     * */
    WARN,
    /**
     * [Log Messages](https://torproject.gitlab.io/torspec/control-spec/#log-messages)
     * */
    ERR,

    /**
     * [New Descriptors Available](https://torproject.gitlab.io/torspec/control-spec/#new-descriptors-available)
     * */
    NEWDESC,

    /**
     * [New Address Mapping](https://torproject.gitlab.io/torspec/control-spec/#new-address-mapping)
     * */
    ADDRMAP,

    /**
     * [Our Descriptor Changed](https://torproject.gitlab.io/torspec/control-spec/#our-descriptor-changed)
     * */
    DESCCHANGED,

    /**
     * [Status Events](https://torproject.gitlab.io/torspec/control-spec/#status-events)
     * */
    STATUS_GENERAL,
    /**
     * [Status Events](https://torproject.gitlab.io/torspec/control-spec/#status-events)
     * */
    STATUS_CLIENT,
    /**
     * [Status Events](https://torproject.gitlab.io/torspec/control-spec/#status-events)
     * */
    STATUS_SERVER,

    /**
     * [Our Set Of Guard Nodes Changed](https://torproject.gitlab.io/torspec/control-spec/#our-set-of-guard-nodes-has-changed)
     * */
    GUARD,

    /**
     * [Network Status Changed](https://torproject.gitlab.io/torspec/control-spec/#network-status-has-changed)
     * */
    NS,

    /**
     * [Bandwidth Used On An Application Stream](https://torproject.gitlab.io/torspec/control-spec/#bandwidth-used-on-an-application-stream)
     * */
    STREAM_BW,

    /**
     * [Per-country Client Stats](https://torproject.gitlab.io/torspec/control-spec/#per-country-client-stats)
     * */
    CLIENTS_SEEN,

    /**
     * [New Consensus Network Status Arrived](https://torproject.gitlab.io/torspec/control-spec/#new-consensus-networkstatus-has-arrived)
     * */
    NEWCONSENSUS,

    /**
     * [New Circuit Build Time Has Been Set](https://torproject.gitlab.io/torspec/control-spec/#new-circuit-buildtime-has-been-set)
     * */
    BUILDTIMEOUT_SET,

    /**
     * [Signal Received](https://torproject.gitlab.io/torspec/control-spec/#signal-received)
     * */
    SIGNAL,

    /**
     * [Configuration Changed](https://torproject.gitlab.io/torspec/control-spec/#configuration-changed)
     * */
    CONF_CHANGED,

    /**
     * [Circuit Status Changed Slightly](https://torproject.gitlab.io/torspec/control-spec/#circuit-status-changed-slightly)
     * */
    CIRC_MINOR,

    /**
     * [Pluggable Transport Launched](https://torproject.gitlab.io/torspec/control-spec/#pluggable-transport-launched)
     * */
    TRANSPORT_LAUNCHED,

    /**
     * [Bandwidth Used On An OR Dir Or Exit Connection](https://torproject.gitlab.io/torspec/control-spec/#bandwidth-used-on-an-or-or-dir-or-exit-connection)
     * */
    CONN_BW,

    /**
     * [Bandwidth Used By All Streams Attached To A Circuit](https://torproject.gitlab.io/torspec/control-spec/#bandwidth-used-by-all-streams-attached-to-a-circuit)
     * */
    CIRC_BW,

    /**
     * [Per-circuit Cell Stats](https://torproject.gitlab.io/torspec/control-spec/#per-circuit-cell-stats)
     * */
    CELL_STATS,

    /**
     * [HiddenService Descriptors](https://torproject.gitlab.io/torspec/control-spec/#hiddenservice-descriptors)
     * */
    HS_DESC,

    /**
     * [HiddenService Descriptors Content](https://torproject.gitlab.io/torspec/control-spec/#hiddenservice-descriptors-content)
     * */
    HS_DESC_CONTENT,

    /**
     * [Network Liveness Changed](https://torproject.gitlab.io/torspec/control-spec/#network-liveness-has-changed)
     * */
    NETWORK_LIVENESS;

//    /**
//     * [Pluggable Transport Logs](https://torproject.gitlab.io/torspec/control-spec/#pluggable-transport-logs)
//     * */
//    PT_LOG,
//
//    /**
//     * [Pluggable Transport Status](https://torproject.gitlab.io/torspec/control-spec/#pluggable-transport-status)
//     * */
//    PT_STATUS,

    /**
     * Create an observer for the given [TorEvent]
     *
     * e.g. (Kotlin)
     *
     *     val bwObserver = TorEvent.BW.observer { event ->
     *         updateNotification(event.formatBandwidth())
     *     }
     *
     * e.g. (Java)
     *
     *     TorEvent.Observer bwObserver = TorEvent.BW.observer(e -> {
     *         updateNotification(formatBandwidth(e));
     *     });
     *
     * @param [block] the callback to pass the event text to
     * */
    public fun observer(
        block: ItBlock<String>,
    ): Observer = observer("", block)

    /**
     * Create an observer for the given [TorEvent] and [tag].
     *
     * This is useful for lifecycle aware components, all of which
     * can be removed with a single call using the [tag] upon
     * component destruction.
     *
     * e.g. (Kotlin)
     *
     *     val bwObserver = TorEvent.BW.observer("my service") { event ->
     *         updateNotification(event.formatBandwidth())
     *     }
     *
     * e.g. (Java)
     *
     *     TorEvent.Observer bwObserver = TorEvent.BW.observer("my service", e -> {
     *         updateNotification(formatBandwidth(e));
     *     });
     *
     * @param [tag] Any non-blank string value
     * @param [block] the callback to pass the event text to
     * */
    public fun observer(
        tag: String,
        block: ItBlock<String>,
    ): Observer = Observer(tag,this, block)

    public class Observer(
        tag: String?,
        @JvmField
        public val event: TorEvent,
        @JvmField
        public val block: ItBlock<String>,
    ) {
        @JvmField
        public val tag: String? = tag?.ifBlank { null }
    }

    /**
     * Base abstraction for implementations that process [TorEvent].
     * */
    public abstract class Processor
    @InternalKmpTorApi
    protected constructor() {

        private val observers = mutableSetOf<Observer>()

        @OptIn(InternalKmpTorApi::class)
        private val lock = SynchronizedObject()

        /**
         * Add a single [Observer].
         * */
        public fun add(observer: Observer) {
            withObservers { add(observer) }
        }

        /**
         * Add multiple [Observer].
         * */
        public fun add(vararg observers: Observer) {
            if (observers.isEmpty()) return
            withObservers { observers.forEach { add(it) } }
        }

        /**
         * Remove a single [Observer].
         * */
        public fun remove(observer: Observer) {
            withObservers { remove(observer) }
        }

        /**
         * Remove multiple [Observer].
         * */
        public fun remove(vararg observers: Observer) {
            if (observers.isEmpty()) return
            withObservers { observers.forEach { remove(it) } }
        }

        /**
         * Remove all [Observer] for given [event]
         * */
        public open fun removeAll(event: TorEvent) {
            withObservers {
                val observers = filter { it.event == event }
                removeAll(observers.toSet())
            }
        }

        /**
         * Remove all [Observer] for given [events]
         * */
        public fun removeAll(vararg events: TorEvent) {
            if (events.isEmpty()) return
            withObservers {
                val observers = filter { events.contains(it.event) }
                removeAll(observers.toSet())
            }
        }

        /**
         * Remove all [Observer] for a given [tag]
         * */
        public open fun removeAll(tag: String) {
            withObservers {
                val observers = filter { it.tag == tag }
                removeAll(observers.toSet())
            }
        }

        /**
         * Removes all [Observer] for all [TorEvent]s.
         * */
        public open fun clearObservers() {
            withObservers { clear() }
        }

        protected fun notifyObservers(event: TorEvent, output: String) {
            withObservers {
                for (observer in this) {
                    if (observer.event != event) continue
                    observer.block.invoke(output)
                }
            }
        }

        protected fun <T: Any?> withObservers(
            block: MutableSet<Observer>.() -> T,
        ): T {
            @OptIn(InternalKmpTorApi::class)
            val result = synchronized(lock) {
                block(observers)
            }

            return result
        }
    }
}
