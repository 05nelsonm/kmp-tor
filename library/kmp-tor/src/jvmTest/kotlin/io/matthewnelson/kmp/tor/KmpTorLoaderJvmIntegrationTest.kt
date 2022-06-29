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
package io.matthewnelson.kmp.tor

import io.matthewnelson.kmp.tor.common.address.PortProxy
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Setting.*
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Option.*
import io.matthewnelson.kmp.tor.helpers.TorTestHelper
import kotlinx.coroutines.*
import org.junit.*

class KmpTorLoaderJvmIntegrationTest: TorTestHelper() {

    override fun testConfig(testProvider: TorConfigProviderJvm): TorConfig {
        return TorConfig.Builder {
            val control = Ports.Control()
            put(control.set(AorDorPort.Value(PortProxy(9150))))
            put(control.set(AorDorPort.Value(PortProxy(9151))))
            put(control.set(AorDorPort.Value(PortProxy(9152))))
            put(control.set(AorDorPort.Value(PortProxy(9153))))

            val dns = Ports.Dns()
            put(dns.set(AorDorPort.Value(PortProxy(9154))))
            put(dns.set(AorDorPort.Value(PortProxy(9155))))
            put(dns.set(AorDorPort.Value(PortProxy(9156))))
            put(dns.set(AorDorPort.Value(PortProxy(9157))))

            val socks = Ports.Socks()
            put(socks.set(AorDorPort.Value(PortProxy(9158))))
            put(socks.set(AorDorPort.Value(PortProxy(9159))))
            put(socks.set(AorDorPort.Value(PortProxy(9160))))
            put(socks.set(AorDorPort.Value(PortProxy(9161))))

            val http = Ports.HttpTunnel()
            put(http.set(AorDorPort.Value(PortProxy(9162))))
            put(http.set(AorDorPort.Value(PortProxy(9163))))
            put(http.set(AorDorPort.Value(PortProxy(9164))))
            put(http.set(AorDorPort.Value(PortProxy(9165))))

            val trans = Ports.Trans()
            put(trans.set(AorDorPort.Value(PortProxy(9166))))
            put(trans.set(AorDorPort.Value(PortProxy(9167))))
            put(trans.set(AorDorPort.Value(PortProxy(9168))))
            put(trans.set(AorDorPort.Value(PortProxy(9169))))

            put(UnixSockets.Control().set(FileSystemFile(
                testProvider.workDir.builder {
                    addSegment(DataDirectory.DEFAULT_NAME)
                    addSegment(UnixSockets.Control.DEFAULT_NAME)
                }
            )))

            put(ClientOnionAuthDir().set(FileSystemDir(
                testProvider.workDir.builder { addSegment(ClientOnionAuthDir.DEFAULT_NAME) }
            )))

            put(DormantClientTimeout().set(Time.Minutes(10)))
            put(DormantCanceledByStartup().set(TorF.True))
        }.build()
    }

    @Test
    fun givenTorManager_whenRestartCalled_returnsSuccess() = runBlocking {
        manager.restart().getOrThrow()

        delay(5_000L)
        manager.restart().getOrThrow()

        Unit
    }
}
