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
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent
import kotlin.collections.ArrayList

class App: Application(), TorManagerEvent.SealedListener {

    val manager: TorManager by lazy {
        val configProvider = object : TorConfigProviderAndroid(context = this@App) {
            override fun provide(): TorConfig {
                return TorConfig.Builder {
                    // Any conflicting or unavailable ports provided will fall
                    // back to "auto".
                    put(Ports.Socks().set(AorDorPort.Value(Port(9150))))
                    put(Ports.HttpTunnel().set(AorDorPort.Value(Port(9150))))
                    put(Ports.Dns().set(AorDorPort.Value(Port(9150))))
                    put(Ports.Trans().set(AorDorPort.Value(Port(9150))))

                    // For Android, disabling & reducing connection padding is
                    // advisable to minimize mobile data usage.
                    put(ConnectionPadding().set(AorTorF.False))
                    put(ConnectionPaddingReduced().set(TorF.True))

                    // Tor defaults to 24h. Reducing to 10 min helps mitigate
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
                    // flag, this is needed to be set.
                    put(ClientOnionAuthDir().set(FileSystemDir(
                        workDir.builder { addSegment(ClientOnionAuthDir.DEFAULT_NAME) }
                    )))
                }.build()
            }
        }

        val loader = KmpTorLoaderAndroid(provider = configProvider)

        TorManager.newInstance(application = this, loader = loader)
    }

    override fun onCreate() {
        super.onCreate()
        manager.debug(true)
        manager.addListener(this)
        data.addLine(TorServiceConfig.getMetaData(this).toString())
    }









    private class EventData {
        private val _data: MutableLiveData<String> = MutableLiveData("")
        val data: LiveData<String> = _data
        private val list: MutableList<String> = ArrayList(50)

        fun addLine(line: String) {
            synchronized(this) {
                if (list.size > 49) {
                    list.removeAt(0)
                }
                list.add(line)
                Log.d("EventListener", line)
                _data.value = list.joinToString("\n")
            }
        }
    }

    private val data = EventData()

    val events: LiveData<String> get() = data.data

    override fun onEvent(event: TorManagerEvent) {
        data.addLine(event.toString())
        if (event is TorManagerEvent.Error) {
            event.value.printStackTrace()
        }
    }

    private var lastBandwidth: String = ""
    override fun onEvent(event: TorEvent.Type.SingleLineEvent, output: String) {
        if (event is TorEvent.BandwidthUsed) {
            if (output != lastBandwidth) {
                lastBandwidth = output
                data.addLine("event=${event.javaClass.simpleName}, output=$output")
            }
        } else {
            data.addLine("event=${event.javaClass.simpleName}, output=$output")
        }
    }

    override fun onEvent(event: TorEvent.Type.MultiLineEvent, output: List<String>) {
        data.addLine("event=${event.javaClass.simpleName}\noutput=${output.joinToString("\n")}")
    }
}
