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
import io.matthewnelson.kmp.tor.common.annotation.InternalTorApi
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Option.AorDorPort
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Setting.Ports
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Setting.UnixSockets
import io.matthewnelson.kmp.tor.controller.common.file.Path
import io.matthewnelson.kmp.tor.controller.common.internal.PlatformUtil
import io.matthewnelson.kmp.tor.manager.common.exceptions.TorManagerException

internal class PortValidator internal constructor(private val dataDir: Path) {

    private val ports: MutableSet<Ports> = mutableSetOf()
    private val unixSockets: MutableSet<UnixSockets> = mutableSetOf()

    var hasControl = false
        private set
    var hasSocks = false
        private set

    fun add(port: Ports) {
        if (!ports.add(port)) {
            return
        }

        if (port is Ports.Control) {
            hasControl = true
        } else if (port is Ports.Socks) {
            hasSocks = true
        }
    }

    @Throws(TorManagerException::class)
    fun add(unixSocket: UnixSockets) {
        if (!unixSocket.isAcceptablePathLength) {
            throw TorManagerException("UnixSocket path must be less than or equal to 105 characters: $unixSocket")
        }

        if (!unixSockets.add(unixSocket)) {
            return
        }

        if (unixSocket is UnixSockets.Control) {
            hasControl = true
        } else if (unixSocket is UnixSockets.Socks) {
            hasSocks = true
        }
    }

    fun validate(isPortAvailable: (Port) -> Boolean): Set<TorConfig.Setting<*>> {
        if (!hasSocks) {
            // Try to add default 9050 so we can validate that it is open
            val socks = Ports.Socks()
            if (!ports.add(socks)) {
                // set to auto if another port type is set to 9050
                socks.set(AorDorPort.Auto)
                ports.add(socks)
            }
            hasSocks = true
        }

        val validated: MutableSet<TorConfig.Setting<*>> = mutableSetOf()

        for (port in ports) {
            when (val option = port.value) {
                is AorDorPort.Auto,
                is AorDorPort.Disable -> {
                    validated.add(port)
                }
                is AorDorPort.Value -> {
                    if (isPortAvailable.invoke(Port(option.port.value))) {
                        validated.add(port)
                    } else {
                        // Unavailable. Set to auto
                        validated.add(port.clone().set(AorDorPort.Auto))
                    }
                }
            }
        }

        if (!hasControl) {
            // Prefer using unix domain socket if it's supported.
            @OptIn(InternalTorApi::class)
            val control = if (PlatformUtil.hasControlUnixDomainSocketSupport) {
                val unix = UnixSockets.Control()
                unix.set(
                    TorConfig.Option.FileSystemFile(
                        dataDir.builder {
                            addSegment(UnixSockets.Control.DEFAULT_NAME)
                        }
                    )
                )

                if (unix.isAcceptablePathLength) {
                    unix
                } else {
                    Ports.Control().set(AorDorPort.Auto)
                }
            } else {
                Ports.Control().set(AorDorPort.Auto)
            }

            if (!validated.add(control)) {
                // Will only be the case if another unix domain socket path
                // is the same as what we set UnixSocket.Control to.
                validated.remove(control)
                validated.add(control)
            }
        }

        validated.addAll(unixSockets)

        return validated
    }
}

@Suppress("NOTHING_TO_INLINE", "KotlinRedundantDiagnosticSuppress")
private inline val UnixSockets.isAcceptablePathLength: Boolean get() = (value?.value?.length ?: 0) <= 105
