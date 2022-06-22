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
package io.matthewnelson.kmp.tor.controller.common.internal

import io.matthewnelson.kmp.tor.common.annotation.InternalTorApi
import io.matthewnelson.kmp.tor.common.util.TorStrings.SP
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Setting.HiddenService

/**
 * Returns false if nothing was appended to the StringBuilder,
 * and true if something was.
 * */
@InternalTorApi
fun TorConfig.Setting<*>.appendTo(
    sb: StringBuilder,
    appendValue: Boolean,
    isWriteTorConfig: Boolean,
): Boolean {
    if (appendValue && this.value == null) {
        return false
    }

    val delimiter = if (isWriteTorConfig) {
        ' '
    } else {
        '='
    }

    when (this) {
        is TorConfig.Setting.AutomapHostsOnResolve,
        is TorConfig.Setting.CacheDirectory,
        is TorConfig.Setting.ClientOnionAuthDir,
        is TorConfig.Setting.ConnectionPadding,
        is TorConfig.Setting.ConnectionPaddingReduced,
        is TorConfig.Setting.ControlPortWriteToFile,
        is TorConfig.Setting.CookieAuthFile,
        is TorConfig.Setting.CookieAuthentication,
        is TorConfig.Setting.DataDirectory,
        is TorConfig.Setting.DisableNetwork,
        is TorConfig.Setting.DormantCanceledByStartup,
        is TorConfig.Setting.DormantClientTimeout,
        is TorConfig.Setting.DormantOnFirstStartup,
        is TorConfig.Setting.DormantTimeoutDisabledByIdleStreams,
        is TorConfig.Setting.GeoIPExcludeUnknown,
        is TorConfig.Setting.GeoIpV4File,
        is TorConfig.Setting.GeoIpV6File,
        is TorConfig.Setting.OwningControllerProcess,
        is TorConfig.Setting.RunAsDaemon,
        is TorConfig.Setting.SyslogIdentityTag -> {
            sb.append(keyword)

            if (!appendValue) {
                return true
            }

            sb.append(delimiter)
            sb.quoteIfTrue(!isWriteTorConfig)
            sb.append(value)
            sb.quoteIfTrue(!isWriteTorConfig)
            return true
        }

        is TorConfig.Setting.UnixSockets -> {
            sb.append(keyword)

            if (!appendValue) {
                return true
            }

            sb.append(delimiter)
            sb.quoteIfTrue(!isWriteTorConfig)
            sb.append("unix:")
            sb.escapeIfTrue(!isWriteTorConfig)
            sb.quote()
            sb.append(value?.value)
            sb.escapeIfTrue(!isWriteTorConfig)
            sb.quote()

            when (this) {
                is TorConfig.Setting.UnixSockets.Control -> {
                    unixFlags?.let { flags ->
                        for (flag in flags) {
                            sb.append(SP)
                            sb.append(flag.value)
                        }
                    }
                }
                is TorConfig.Setting.UnixSockets.Socks -> {
                    flags?.let { flags ->
                        for (flag in flags) {
                            sb.append(SP)
                            sb.append(flag.value)
                        }
                    }
                    unixFlags?.let { flags ->
                        for (flag in flags) {
                            sb.append(SP)
                            sb.append(flag.value)
                        }
                    }
                    isolationFlags?.let { flags ->
                        for (flag in flags) {
                            sb.append(SP)
                            sb.append(flag.value)
                        }
                    }
                }
            }

            sb.quoteIfTrue(!isWriteTorConfig)
            return true
        }

        is TorConfig.Setting.Ports -> {
            sb.append(keyword)

            if (!appendValue) {
                return true
            }

            sb.append(delimiter)
            sb.quoteIfTrue(!isWriteTorConfig)
            sb.append(value)

            when (this) {
                is TorConfig.Setting.Ports.Control -> {}
                is TorConfig.Setting.Ports.Dns -> {
                    isolationFlags?.let { flags ->
                        for (flag in flags) {
                            sb.append(SP)
                            sb.append(flag.value)
                        }
                    }
                }
                is TorConfig.Setting.Ports.HttpTunnel -> {
                    isolationFlags?.let { flags ->
                        for (flag in flags) {
                            sb.append(SP)
                            sb.append(flag.value)
                        }
                    }
                }
                is TorConfig.Setting.Ports.Socks -> {
                    flags?.let { flags ->
                        for (flag in flags) {
                            sb.append(SP)
                            sb.append(flag.value)
                        }
                    }
                    isolationFlags?.let { flags ->
                        for (flag in flags) {
                            sb.append(SP)
                            sb.append(flag.value)
                        }
                    }
                }
                is TorConfig.Setting.Ports.Trans -> {
                    isolationFlags?.let { flags ->
                        for (flag in flags) {
                            sb.append(SP)
                            sb.append(flag.value)
                        }
                    }
                }
            }

            sb.quoteIfTrue(!isWriteTorConfig)
            return true
        }

        is HiddenService -> {
            if (!appendValue) {
                sb.append(keyword)
                sb.append(SP)
                sb.append("HiddenServicePort")
                sb.append(SP)
                sb.append("HiddenServiceMaxStreams")
                sb.append(SP)
                sb.append("HiddenServiceMaxStreamsCloseCircuit")
                return true
            }

            val hsDirPath = value ?: return false
            val hsPorts = ports

            if (hsPorts == null || hsPorts.isEmpty()) {
                return false
            }

            sb.newLineIfTrue(isWriteTorConfig)
            sb.append(keyword)
            sb.append(delimiter)
            sb.quoteIfTrue(!isWriteTorConfig)
            sb.append(hsDirPath.value)
            sb.quoteIfTrue(!isWriteTorConfig)

            val localhostIp: String = try {
                ControllerUtils.localhostAddress()
            } catch (_: Exception) {
                "127.0.0.1"
            }

            for (hsPort in hsPorts) {
                sb.newLineIfTrue(isWriteTorConfig) {
                    // if false
                    append(SP)
                }

                sb.append("HiddenServicePort")
                sb.append(delimiter)
                sb.quoteIfTrue(!isWriteTorConfig)

                when (hsPort) {
                    is HiddenService.Ports -> {
                        sb.append(hsPort.virtualPort.value)
                        sb.append(SP)
                        sb.append(localhostIp)
                        sb.append(':')
                        sb.append(hsPort.targetPort.value)
                    }
                    is HiddenService.UnixSocket -> {
                        sb.append(hsPort.virtualPort.value)
                        sb.append(SP)
                        sb.append("unix:")
                        sb.escapeIfTrue(!isWriteTorConfig)
                        sb.quote()
                        sb.append(hsPort.targetUnixSocket.value)
                        sb.escapeIfTrue(!isWriteTorConfig)
                        sb.quote()
                    }
                }

                sb.quoteIfTrue(!isWriteTorConfig)
            }

            sb.newLineIfTrue(isWriteTorConfig) {
                // if false
                append(SP)
            }

            sb.append("HiddenServiceMaxStreams")
            sb.append(delimiter)
            sb.quoteIfTrue(!isWriteTorConfig)
            sb.append(maxStreams?.value ?: "0")
            sb.quoteIfTrue(!isWriteTorConfig)

            sb.newLineIfTrue(isWriteTorConfig) {
                // if false
                append(SP)
            }

            sb.append("HiddenServiceMaxStreamsCloseCircuit")
            sb.append(delimiter)
            sb.quoteIfTrue(!isWriteTorConfig)
            sb.append(maxStreamsCloseCircuit?.value ?: "0")
            sb.newLineIfTrue(isWriteTorConfig) {
                // if false
                quote()
                this
            }
            return true
        }
    }
}

@Suppress("nothing_to_inline")
private inline fun StringBuilder.quoteIfTrue(addQuote: Boolean) {
    if (addQuote) {
        quote()
    }
}

@Suppress("nothing_to_inline")
private inline fun StringBuilder.quote() {
    append('"')
}

@Suppress("nothing_to_inline")
private inline fun StringBuilder.escapeIfTrue(addEscape: Boolean) {
    if (addEscape) {
        escape()
    }
}

@Suppress("nothing_to_inline")
private inline fun StringBuilder.escape() {
    append('\\')
}

@Suppress("nothing_to_inline")
private inline fun StringBuilder.newLineIfTrue(addLine: Boolean): Boolean {
    if (addLine) {
        appendLine()
    }

    return addLine
}

@Suppress("nothing_to_inline")
private inline fun StringBuilder.newLineIfTrue(
    addLine: Boolean,
    otherwise: StringBuilder.() -> StringBuilder
) {
    if (!newLineIfTrue(addLine)) {
        otherwise.invoke(this)
    }
}
