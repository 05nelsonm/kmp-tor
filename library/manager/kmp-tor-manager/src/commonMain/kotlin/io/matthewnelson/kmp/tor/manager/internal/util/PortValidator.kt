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
package io.matthewnelson.kmp.tor.manager.internal.util

import io.matthewnelson.kmp.tor.common.address.Port
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Option.AorDorPort
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Setting.Ports

internal class PortValidator internal constructor() {
    private val configPorts: MutableSet<Ports> = mutableSetOf()
    var hasControlPort = false
        private set
    var hasSocksPort = false
        private set

    fun add(port: Ports) {
        if (port is Ports.Control) {
            hasControlPort = true
        } else if (port is Ports.Socks) {
            hasSocksPort = true
        }
        configPorts.add(port)
    }

    @Suppress("unchecked_cast")
    fun validate(isPortAvailable: (Port) -> Boolean): Set<Ports> {
        if (!hasSocksPort) {
            // Try to add default 9050 so we can validate that it is open
            val socks = Ports.Socks()
            if (!configPorts.add(socks)) {
                // set to auto if another port type is set to 9050
                socks.set(AorDorPort.Auto)
                configPorts.add(socks)
            }
            hasSocksPort = true
        }

        val validatedPorts: MutableSet<Ports> = mutableSetOf()

        for (port in configPorts) {
            when (val option = port.value) {
                is AorDorPort.Auto,
                is AorDorPort.Disable -> {
                    validatedPorts.add(port)
                }
                is AorDorPort.Value -> {
                    if (isPortAvailable.invoke(option.port)) {
                        validatedPorts.add(port)
                    } else {
                        // Unavailable. Set to auto
                        validatedPorts.add(port.clone().set(AorDorPort.Auto) as Ports)
                    }
                }
            }
        }

        if (!hasControlPort) {
            validatedPorts.add(Ports.Control().set(AorDorPort.Auto) as Ports)
        }

        return validatedPorts
    }
}
