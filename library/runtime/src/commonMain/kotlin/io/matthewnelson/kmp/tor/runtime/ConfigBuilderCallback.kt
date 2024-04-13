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
package io.matthewnelson.kmp.tor.runtime

import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.tor.core.api.ResourceInstaller
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.core.ThisBlock
import io.matthewnelson.kmp.tor.runtime.core.TorConfig
import io.matthewnelson.kmp.tor.runtime.core.address.Port.Proxy.Companion.toPortProxy
import io.matthewnelson.kmp.tor.runtime.core.builder.ExtendedTorConfigBuilder
import io.matthewnelson.kmp.tor.runtime.core.builder.UnixSocketBuilder
import kotlin.jvm.JvmSynthetic

/**
 * A Callback for configuring [TorConfig.Builder].
 *
 * @see [TorRuntime.Builder.config]
 * */
public fun interface ConfigBuilderCallback: ThisBlock.WithIt<TorConfig.Builder, TorRuntime.Environment> {

    public companion object {

        /**
         * After all [ConfigBuilderCallback] have been applied, this is called
         * in order to ensure minimum settings required for [TorRuntime] are had.
         * */
        @JvmSynthetic
        @OptIn(InternalKmpTorApi::class)
        internal fun TorConfig.Builder.putDefaults(
            environment: TorRuntime.Environment,
            omitGeoIPFileSettings: Boolean,
            paths: ResourceInstaller.Paths.Tor,
        ) {
            // Dirs/Files
            if (!omitGeoIPFileSettings) {
                put(TorConfig.GeoIPFile) { file = paths.geoip }
                put(TorConfig.GeoIPv6File) { file = paths.geoip6 }
            }

            putIfAbsent(TorConfig.DataDirectory) {
                directory = environment.workDir
                    .resolve(TorConfig.DataDirectory.DEFAULT_NAME)
            }
            putIfAbsent(TorConfig.CacheDirectory) {
                directory = environment.cacheDir
            }
            putIfAbsent(TorConfig.ControlPortWriteToFile) {
                file = environment.workDir
                    .resolve(TorConfig.ControlPortWriteToFile.DEFAULT_NAME)
            }

            // If not declared specifically, add it. Tor defaults to using
            // false and that can be very problematic if default timeout of
            // 24h is reached and a restart is performed (even after application
            // is re-started)... Not a well thought feature, this smooths it
            // over while still providing choice by only adding if not declared.
            putIfAbsent(TorConfig.DormantCanceledByStartup) { cancel = true }

            // Authentication
            if (
                !(this as ExtendedTorConfigBuilder).contains(TorConfig.CookieAuthFile)
            // && !contains(TorConfig.HashedControlPassword)
            ) {
                put(TorConfig.CookieAuthFile) {
                    file = environment.workDir
                        .resolve(TorConfig.CookieAuthFile.DEFAULT_NAME)
                }
            }

            // Ports
            if (!contains(TorConfig.__SocksPort)) {
                // Add default socks port so that port availability check
                // can reassign it to auto if needed
                put(TorConfig.__SocksPort) {
                    asPort {
                        port(9050.toPortProxy())
                        reassignable(allow = true)
                    }
                }
            }

            if (!contains(TorConfig.__ControlPort)) {
                put(TorConfig.__ControlPort) {
                    try {
                        // Prefer using Unix Domain Sockets whenever possible
                        // because of things like Airplane Mode.
                        asUnixSocket {
                            file = environment.workDir
                                .resolve(UnixSocketBuilder.DEFAULT_NAME_CTRL)
                        }
                    } catch (_: UnsupportedOperationException) {
                        // fallback to TCP if unavailable on the system
                        asPort { auto() }
                    }
                }
            }

            // Required for TorRuntime
            put(TorConfig.DisableNetwork) { disable = true }
            put(TorConfig.RunAsDaemon) { enable = false }
            put(TorConfig.__OwningControllerProcess) { /* default */ }
        }
    }

}
