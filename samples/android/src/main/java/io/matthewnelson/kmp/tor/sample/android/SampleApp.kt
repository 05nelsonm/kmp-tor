/*
 * Copyright (c) 2022 Matthew Nelson
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
package io.matthewnelson.kmp.tor.sample.android

import android.app.Application
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.matthewnelson.kmp.tor.TorConfigProviderAndroid
import io.matthewnelson.kmp.tor.KmpTorLoaderAndroid
import io.matthewnelson.kmp.tor.common.address.Port
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Setting.*
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Option.*
import io.matthewnelson.kmp.tor.controller.common.events.TorEvent
import io.matthewnelson.kmp.tor.manager.TorManager
import io.matthewnelson.kmp.tor.manager.TorServiceConfig
import io.matthewnelson.kmp.tor.manager.common.TorControlManager
import io.matthewnelson.kmp.tor.manager.common.TorOperationManager
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent
import io.matthewnelson.kmp.tor.manager.common.state.TorNetworkState
import io.matthewnelson.kmp.tor.manager.common.state.TorState
import kotlin.collections.ArrayList

class SampleApp: Application() {

    private val manager: TorManager by lazy {
        val configProvider = object : TorConfigProviderAndroid(context = this@SampleApp) {
            override fun provide(): TorConfig {
                return TorConfig.Builder {
                    // Set multiple ports for all of the things
                    val dns = Ports.Dns()
                    put(dns.set(AorDorPort.Value(Port(9252))))
                    put(dns.set(AorDorPort.Value(Port(9253))))

                    val socks = Ports.Socks()
                    put(socks.set(AorDorPort.Value(Port(9254))))
                    put(socks.set(AorDorPort.Value(Port(9255))))

                    val http = Ports.HttpTunnel()
                    put(http.set(AorDorPort.Value(Port(9258))))
                    put(http.set(AorDorPort.Value(Port(9259))))

                    val trans = Ports.Trans()
                    put(trans.set(AorDorPort.Value(Port(9262))))
                    put(trans.set(AorDorPort.Value(Port(9263))))

                    // If a port (9263) is already taken (by ^^^^ trans port above)
                    // this will take its place and "overwrite" the trans port entry
                    // because port 9263 is taken.
                    put(socks.set(AorDorPort.Value(Port(9263))))

                    // Set Flags
                    socks.setFlags(setOf(
                        Ports.Socks.Flag.OnionTrafficOnly
                    )).setIsolationFlags(setOf(
                        Ports.IsolationFlag.IsolateClientAddr
                    )).set(AorDorPort.Value(Port(9264)))
                    put(socks)

                    // reset our socks object to defaults
                    socks.setDefault()

                    // Not necessary, as if ControlPort is missing it will be
                    // automatically added for you; but for demonstration purposes...
                    put(Ports.Control().set(AorDorPort.Auto))

                    // For Android, disabling & reducing connection padding is
                    // advisable to minimize mobile data usage.
                    put(ConnectionPadding().set(AorTorF.False))
                    put(ConnectionPaddingReduced().set(TorF.True))

                    // Tor default is 24h. Reducing to 10 min helps mitigate
                    // unnecessary mobile data usage.
                    put(DormantClientTimeout().set(Time.Minutes(10)))

                    // Tor defaults this setting to false which would mean if
                    // Tor goes dormant, the next time it is started it will still
                    // be in the dormant state and will not bootstrap until being
                    // set to "active". This ensures that if it is a fresh start,
                    // dormancy will be cancelled automatically.
                    put(DormantCanceledByStartup().set(TorF.True))

                    // If planning to use v3 Client Authentication in a persistent
                    // manner (where private keys are saved to disk via the "Persist"
                    // flag), this is needed to be set.
                    put(ClientOnionAuthDir().set(FileSystemDir(
                        workDir.builder { addSegment(ClientOnionAuthDir.DEFAULT_NAME) }
                    )))
                }.build()
            }
        }

        val loader = KmpTorLoaderAndroid(provider = configProvider)

        TorManager.newInstance(application = this, loader = loader, requiredEvents = null)
    }

    // only expose necessary interfaces
    val torOperationManager: TorOperationManager get() = manager
    val torControlManager: TorControlManager get() = manager

    private val listener = SampleListener()
    val events: LiveData<String> get() = listener.eventLines
    val addressInfo: LiveData<TorManagerEvent.AddressInfo> get() = listener.addressInfo
    val state: LiveData<TorManagerEvent.State> get() = listener.state

    override fun onCreate() {
        super.onCreate()
        manager.debug(true)
        manager.addListener(listener)
        listener.addLine(TorServiceConfig.getMetaData(this).toString())
    }

    private class SampleListener: TorManagerEvent.Listener() {
        private val _eventLines: MutableLiveData<String> = MutableLiveData("")
        val eventLines: LiveData<String> = _eventLines
        private val events: MutableList<String> = ArrayList(50)

        fun addLine(line: String) {
            synchronized(this) {
                if (events.size > 49) {
                    events.removeAt(0)
                }
                events.add(line)
                Log.d("SampleListener", line)
                _eventLines.value = events.joinToString("\n")
            }
        }

        private val _addressInfo: MutableLiveData<TorManagerEvent.AddressInfo> =
            MutableLiveData(TorManagerEvent.AddressInfo())
        val addressInfo: LiveData<TorManagerEvent.AddressInfo> = _addressInfo
        override fun managerEventAddressInfo(info: TorManagerEvent.AddressInfo) {
            _addressInfo.value = info
        }

        private val _state: MutableLiveData<TorManagerEvent.State> =
            MutableLiveData(TorManagerEvent.State(TorState.Off, TorNetworkState.Disabled))
        val state: LiveData<TorManagerEvent.State> = _state
        override fun managerEventState(state: TorManagerEvent.State) {
            _state.value = state
        }

        override fun onEvent(event: TorManagerEvent) {
            addLine(event.toString())
            if (event is TorManagerEvent.Log.Error) {
                event.value.printStackTrace()
            }

            super.onEvent(event)
        }

        private var lastBandwidth: String = ""
        override fun onEvent(event: TorEvent.Type.SingleLineEvent, output: String) {
            if (event is TorEvent.BandwidthUsed) {
                if (output != lastBandwidth) {
                    lastBandwidth = output
                    addLine("event=${event.javaClass.simpleName}, output=$output")
                }
            } else {
                addLine("event=${event.javaClass.simpleName}, output=$output")
            }
        }

        override fun onEvent(event: TorEvent.Type.MultiLineEvent, output: List<String>) {
            addLine("-------------- multi-line event START: ${event.javaClass.simpleName} --------------")
            for (line in output) {
                addLine(line)
            }
            addLine("--------------- multi-line event END: ${event.javaClass.simpleName} ---------------")
        }
    }

}
