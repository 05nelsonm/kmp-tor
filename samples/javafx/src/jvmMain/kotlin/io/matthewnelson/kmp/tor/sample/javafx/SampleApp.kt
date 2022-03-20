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
package io.matthewnelson.kmp.tor.sample.javafx

import io.matthewnelson.kmp.tor.KmpTorLoaderJvm
import io.matthewnelson.kmp.tor.PlatformInstaller
import io.matthewnelson.kmp.tor.PlatformInstaller.InstallOption
import io.matthewnelson.kmp.tor.TorConfigProviderJvm
import io.matthewnelson.kmp.tor.common.address.Port
import io.matthewnelson.kmp.tor.common.address.PortProxy
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Setting.*
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Option.*
import io.matthewnelson.kmp.tor.controller.common.events.TorEvent
import io.matthewnelson.kmp.tor.controller.common.file.Path
import io.matthewnelson.kmp.tor.manager.TorManager
import io.matthewnelson.kmp.tor.manager.common.TorControlManager
import io.matthewnelson.kmp.tor.manager.common.TorOperationManager
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent
import io.matthewnelson.kmp.tor.manager.common.state.TorNetworkState
import io.matthewnelson.kmp.tor.manager.common.state.TorState
import io.matthewnelson.kmp.tor.sample.javafx.ui.SampleView
import io.matthewnelson.kmp.tor.sample.javafx.util.Log
import javafx.application.Platform
import javafx.stage.Stage
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tornadofx.*

class SampleApp: App(SampleView::class) {

    /**
     * Instantiate [TorManager]
     *
     * @see [PlatformInstaller]
     * @see [TorConfigProviderJvm]
     * @see [KmpTorLoaderJvm]
     * @see [TorManager.newInstance]
     * */
    private val manager: TorManager by lazy {
        val osName = System.getProperty("os.name")

        val platformInstaller = when {
            osName.contains("Windows") -> {
                PlatformInstaller.mingwX64(InstallOption.CleanInstallIfMissing)
            }
            osName.contains("Mac") || osName.contains("Darwin") -> {
                PlatformInstaller.macosX64(InstallOption.CleanInstallIfMissing)
            }
            osName.contains("Linux") -> {
                PlatformInstaller.linuxX64(InstallOption.CleanInstallIfMissing)
            }
            else -> {
                throw RuntimeException("Could not identify OS from 'os.name=$osName'")
            }
        }

        Log.d(this.javaClass.simpleName, "Setting up KmpTor for os: ${platformInstaller.os}, arch: ${platformInstaller.arch}")

        // Note that for this example the temp directory is utilized. Keep in mind
        // that all processes and users have access to the temporary directory and
        // its use should be avoided in production.
        val tmpDir: String = System.getProperty("java.io.tmpdir")
            ?: throw RuntimeException("Could not identify OS's temporary directory")

        val configProvider = object: TorConfigProviderJvm() {
            override val workDir: Path = Path(tmpDir).builder {
                addSegment("kmptor-javafx-sample")
                addSegment("work")
            }
            override val cacheDir: Path = Path(tmpDir).builder {
                addSegment("kmptor-javafx-sample")
                addSegment("cache")
            }

            override fun provide(): TorConfig {
                return TorConfig.Builder {
                    // Set multiple ports for all of the things
                    val dns = Ports.Dns()
                    put(dns.set(AorDorPort.Value(PortProxy(9252))))
                    put(dns.set(AorDorPort.Value(PortProxy(9253))))

                    val socks = Ports.Socks()
                    put(socks.set(AorDorPort.Value(PortProxy(9254))))
                    put(socks.set(AorDorPort.Value(PortProxy(9255))))

                    val http = Ports.HttpTunnel()
                    put(http.set(AorDorPort.Value(PortProxy(9258))))
                    put(http.set(AorDorPort.Value(PortProxy(9259))))

                    val trans = Ports.Trans()
                    put(trans.set(AorDorPort.Value(PortProxy(9262))))
                    put(trans.set(AorDorPort.Value(PortProxy(9263))))

                    // If a port (9263) is already taken (by ^^^^ trans port above)
                    // this will take its place and "overwrite" the trans port entry
                    // because port 9263 is taken.
                    put(socks.set(AorDorPort.Value(PortProxy(9263))))

                    // Set Flags
                    socks.setFlags(setOf(
                        Ports.Socks.Flag.OnionTrafficOnly
                    )).setIsolationFlags(setOf(
                        Ports.IsolationFlag.IsolateClientAddr
                    )).set(AorDorPort.Value(PortProxy(9264)))
                    put(socks)

                    // reset our socks object to defaults
                    socks.setDefault()

                    // Not necessary, as if ControlPort is missing it will be
                    // automatically added for you; but for demonstration purposes...
                    put(Ports.Control().set(AorDorPort.Auto))

                    // Tor defaults this setting to false which would mean if
                    // Tor goes dormant (default is after 24h), the next time it
                    // is started it will still be in the dormant state and will
                    // not bootstrap until being set to "active". This ensures that
                    // if it is a fresh start, dormancy will be cancelled automatically.
                    put(DormantCanceledByStartup().set(TorF.True))

                    // If planning to use v3 Client Authentication in a persistent
                    // manner (where private keys are saved to disk via the "Persist"
                    // flag), this is needed to be set.
                    put(ClientOnionAuthDir().set(FileSystemDir(
                        workDir.builder { addSegment(ClientOnionAuthDir.DEFAULT_NAME) }
                    )))

                    // Add Hidden services
                    put(HiddenService()
                        .setPorts(ports = setOf(
                            HiddenService.Ports(virtualPort = Port(1025), targetPort = Port(1027)),
                            HiddenService.Ports(virtualPort = Port(1026), targetPort = Port(1027))
                        ))
                        .setMaxStreams(maxStreams = HiddenService.MaxStreams(value = 2))
                        .setMaxStreamsCloseCircuit(value = TorF.True)
                        .set(FileSystemDir(
                            workDir.builder {
                                addSegment(HiddenService.DEFAULT_PARENT_DIR_NAME)
                                addSegment("test_service")
                            }
                        ))
                    )

                    put(HiddenService()
                        .setPorts(ports = setOf(
                            HiddenService.Ports(virtualPort = Port(1028), targetPort = Port(1030)),
                            HiddenService.Ports(virtualPort = Port(1029), targetPort = Port(1030))
                        ))
                        .set(FileSystemDir(
                            workDir.builder {
                                addSegment(HiddenService.DEFAULT_PARENT_DIR_NAME)
                                addSegment("test_service_2")
                            }
                        ))
                    )
                }.build()
            }
        }

        val jvmLoader = KmpTorLoaderJvm(installer = platformInstaller, provider = configProvider)

        TorManager.newInstance(loader = jvmLoader, networkObserver = null, requiredEvents = null)
    }

    // only expose necessary interfaces
    val torOperationManager: TorOperationManager get() = manager
    val torControlManager: TorControlManager get() = manager

    private val listener = SampleListener()
    val eventLines: StateFlow<String> get() = listener.eventLines
    val addressInfo: StateFlow<TorManagerEvent.AddressInfo> get() = listener.addressInfo
    val state: StateFlow<TorManagerEvent.State> get() = listener.state

    override fun start(stage: Stage) {
        Log.d(this.javaClass.simpleName, "start")
        manager.debug(true)
        manager.addListener(listener)

        // TODO: Move to SampleView along with stop/restart buttons
        manager.startQuietly()

        setupOnCloseIntercept(stage)

        super.start(stage)
    }

    /**
     * Must call [TorManager.destroy] to stop Tor and clean up so that the
     * Application does not hang on exit.
     *
     * See [stop] also.
     * */
    private fun setupOnCloseIntercept(stage: Stage) {
        stage.setOnCloseRequest { event ->
            // `destroy` launches a coroutine using TorManager's scope in order
            // to stop Tor cleanly via it's control port. This takes ~500ms if Tor
            // is running.
            //
            // Upon destruction completion, Platform.exit() will be invoked.
            manager.destroy(stopCleanly = true) {
                // onCompletion
                Platform.exit()
            }
            event.consume()
        }
    }

    override fun stop() {
        super.stop()
        Log.d(this.javaClass.simpleName, "stop")

        // just in case setupOnCloseIntercept fails.
        manager.destroy(stopCleanly = false) {
            // will not be invoked if TorManager has already been destroyed
            Log.w(this.javaClass.simpleName, "onCloseRequest intercept failed. Tor did not stop cleanly.")
        }
    }

    /**
     * Listen for and react to [TorManagerEvent]s && [TorEvent]s
     * */
    private class SampleListener: TorManagerEvent.Listener() {
        private val _eventLines: MutableStateFlow<String> = MutableStateFlow("")
        val eventLines: StateFlow<String> = _eventLines.asStateFlow()
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

        private val _addressInfo: MutableStateFlow<TorManagerEvent.AddressInfo> =
            MutableStateFlow(TorManagerEvent.AddressInfo())
        val addressInfo: StateFlow<TorManagerEvent.AddressInfo> = _addressInfo.asStateFlow()
        override fun managerEventAddressInfo(info: TorManagerEvent.AddressInfo) {
            _addressInfo.value = info
        }

        private val _state: MutableStateFlow<TorManagerEvent.State> =
            MutableStateFlow(TorManagerEvent.State(TorState.Off, TorNetworkState.Disabled))
        val state: StateFlow<TorManagerEvent.State> = _state.asStateFlow()
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

        override fun onEvent(event: TorEvent.Type.SingleLineEvent, output: String) {
            addLine("event=${event.javaClass.simpleName}, output=$output")
        }

        override fun onEvent(event: TorEvent.Type.MultiLineEvent, output: List<String>) {
            addLine("multi-line event: ${event.javaClass.simpleName}. See Logs.")

            // these events are many many many lines and should be moved
            // off the main thread if ever needed to be dealt with.
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch {
                Log.d("SampleListener", "-------------- multi-line event START: ${event.javaClass.simpleName} --------------")
                for (line in output) {
                    Log.d("SampleListener", line)
                }
                Log.d("SampleListener", "--------------- multi-line event END: ${event.javaClass.simpleName} ---------------")
            }
        }
    }

}
