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

import io.matthewnelson.kmp.tor.common.address.*
import io.matthewnelson.kmp.tor.common.annotation.InternalTorApi
import io.matthewnelson.kmp.tor.common.internal.TorStrings.SP
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.KeyWord
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Option.*
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Setting.*

/**
 * Returns false if nothing was appended to the StringBuilder,
 * and true if something was.
 * */
@InternalTorApi
fun TorConfig.Setting<*>.appendTo(sb: StringBuilder, isWriteTorConfig: Boolean): Boolean {
    val value: TorConfig.Option = when(val v = value) {
        null -> return false
        is FileSystemFile -> FileSystemFile(v.path.writeEscapedIfTrue(!isWriteTorConfig))
        is FileSystemDir -> FileSystemDir(v.path.writeEscapedIfTrue(!isWriteTorConfig))
        else -> v
    }

    val delimiter = if (isWriteTorConfig) {
        ' '
    } else {
        '='
    }

    @Suppress("DEPRECATION")
    when (this) {
        is AutomapHostsOnResolve,
        is CacheDirectory,
        is ClientOnionAuthDir,
        is ConnectionPadding,
        is ConnectionPaddingReduced,
        is ControlPortWriteToFile,
        is CookieAuthFile,
        is CookieAuthentication,
        is DataDirectory,
        is DisableNetwork,
        is DormantCanceledByStartup,
        is DormantClientTimeout,
        is DormantOnFirstStartup,
        is DormantTimeoutDisabledByIdleStreams,
        is GeoIPExcludeUnknown,
        is GeoIPFile,
        is GeoIpV6File,
        is RunAsDaemon,
        is SyslogIdentityTag,
        is VirtualAddrNetworkIPv4,
        is VirtualAddrNetworkIPv6,
        is __OwningControllerProcess,

        // DEPRECATED
        is GeoIpV4File,
        is OwningControllerProcess,

        -> {
            sb.append(keyword)
            sb.append(delimiter)
            sb.quoteIfTrue(!isWriteTorConfig)
            sb.append(value.value)
            sb.quoteIfTrue(!isWriteTorConfig)
            return true
        }

        is UnixSockets -> {
            sb.append(keyword)
            sb.append(delimiter)
            sb.quoteIfTrue(!isWriteTorConfig)
            sb.append("unix:")
            sb.escapeIfTrue(!isWriteTorConfig)
            sb.quote()
            sb.append(value.value)
            sb.escapeIfTrue(!isWriteTorConfig)
            sb.quote()

            when (this) {
                is UnixSockets.Control -> {
                    unixFlags?.let { flags ->
                        for (flag in flags) {
                            sb.append(SP)
                            sb.append(flag.value)
                        }
                    }
                }
                is UnixSockets.Socks -> {
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

        is Ports -> {
            sb.append(keyword)
            sb.append(delimiter)
            sb.quoteIfTrue(!isWriteTorConfig)
            sb.append(value.value)

            when (this) {
                is Ports.Control -> {}
                is Ports.Dns -> {
                    isolationFlags?.let { flags ->
                        for (flag in flags) {
                            sb.append(SP)
                            sb.append(flag.value)
                        }
                    }
                }
                is Ports.HttpTunnel -> {
                    isolationFlags?.let { flags ->
                        for (flag in flags) {
                            sb.append(SP)
                            sb.append(flag.value)
                        }
                    }
                }
                is Ports.Socks -> {
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
                is Ports.Trans -> {
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
            val hsPorts = ports

            if (hsPorts == null || hsPorts.isEmpty()) {
                return false
            }

            sb.newLineIfTrue(isWriteTorConfig)
            sb.append(keyword)
            sb.append(delimiter)
            sb.quoteIfTrue(!isWriteTorConfig)
            sb.append(value.value)
            sb.quoteIfTrue(!isWriteTorConfig)

            val localhostIp: IPAddress = try {
                PlatformUtil.localhostAddress()
            } catch (_: Exception) {
                IPAddressV4("127.0.0.1")
            }

            for (hsPort in hsPorts) {
                sb.newLineIfTrue(isWriteTorConfig) {
                    // if false
                    append(SP)
                }

                sb.append(KeyWord.HiddenServicePort)
                sb.append(delimiter)
                sb.quoteIfTrue(!isWriteTorConfig)

                when (hsPort) {
                    is HiddenService.Ports -> {
                        sb.append(hsPort.virtualPort.value)
                        sb.append(SP)
                        sb.append(localhostIp.canonicalHostname())
                        sb.append(':')
                        sb.append(hsPort.targetPort.value)
                    }
                    is HiddenService.UnixSocket -> {
                        sb.append(hsPort.virtualPort.value)
                        sb.append(SP)
                        sb.append("unix:")
                        sb.escapeIfTrue(!isWriteTorConfig)
                        sb.quote()
                        sb.append(hsPort.targetUnixSocket.writeEscapedIfTrue(!isWriteTorConfig).value)
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

            sb.append(KeyWord.HiddenServiceMaxStreams)
            sb.append(delimiter)
            sb.quoteIfTrue(!isWriteTorConfig)
            sb.append(maxStreams?.value ?: AorDorPort.Disable.value)
            sb.quoteIfTrue(!isWriteTorConfig)

            sb.newLineIfTrue(isWriteTorConfig) {
                // if false
                append(SP)
            }

            sb.append(KeyWord.HiddenServiceMaxStreamsCloseCircuit)
            sb.append(delimiter)
            sb.quoteIfTrue(!isWriteTorConfig)
            sb.append(maxStreamsCloseCircuit?.value ?: TorF.False)
            sb.newLineIfTrue(isWriteTorConfig) {
                // if false
                quote()
            }
            return true
        }
    }
}

/**
 * Returns null in the event of an [EmptySet] is a result of filtering.
 *
 * A returned non-null [Set] will have at least 1 item in it.
 * */
@InternalTorApi
fun Set<HiddenService.VirtualPort>.filterSupportedOnly(): Set<HiddenService.VirtualPort>? {
    val filtered =  filter { instance ->
        when (instance) {
            is HiddenService.Ports -> true
            is HiddenService.UnixSocket -> {
                if (PlatformUtil.isDarwin || PlatformUtil.isLinux) {
                    instance.targetUnixSocket.isUnixPath
                } else {
                    false
                }
            }
        }
    }

    return filtered.ifEmpty { null }?.toSet()
}

@Suppress("nothing_to_inline")
private inline fun StringBuilder.quoteIfTrue(addQuote: Boolean): StringBuilder {
    if (addQuote) {
        quote()
    }

    return this
}

@Suppress("nothing_to_inline")
private inline fun StringBuilder.quote(): StringBuilder {
    return append('"')
}

@Suppress("nothing_to_inline")
private inline fun StringBuilder.escapeIfTrue(addEscape: Boolean): StringBuilder {
    if (addEscape) {
        escape()
    }

    return this
}

@Suppress("nothing_to_inline")
private inline fun StringBuilder.escape(): StringBuilder {
    return append('\\')
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
