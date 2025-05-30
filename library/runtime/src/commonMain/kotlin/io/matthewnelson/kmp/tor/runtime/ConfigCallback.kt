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
@file:Suppress("ProtectedInFinal", "UnnecessaryOptInAnnotation")

package io.matthewnelson.kmp.tor.runtime

import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.tor.common.api.GeoipFiles
import io.matthewnelson.kmp.tor.common.api.InternalKmpTorApi
import io.matthewnelson.kmp.tor.common.api.ResourceLoader
import io.matthewnelson.kmp.tor.runtime.core.config.TorConfig
import io.matthewnelson.kmp.tor.runtime.core.config.TorOption
import io.matthewnelson.kmp.tor.runtime.core.config.builder.BuilderScopePort
import io.matthewnelson.kmp.tor.runtime.core.config.builder.RealBuilderScopeTorConfig
import kotlin.jvm.JvmSynthetic

/**
 * A Callback for configuring [TorConfig.BuilderScope].
 *
 * e.g.
 *
 *     ConfigCallback { environment ->
 *         TorOption.__SocksPort.configure { auto() }
 *
 *         TorOption.ClientOnionAuthDir.configure(directory =
 *             environment.workDirectory
 *                 .resolve("auth_private_files")
 *         )
 *
 *         // ...
 *     }
 *
 * @see [TorRuntime.BuilderScope.config]
 * */
public fun interface ConfigCallback {

    public operator fun TorConfig.BuilderScope.invoke(environment: TorRuntime.Environment)

    /**
     * After all [ConfigCallback] for [TorRuntime.BuilderScope.config] have
     * been applied, defaults are then configured in order to ensure minimum
     * settings required for [TorRuntime] are had.
     * */
    public class Defaults private constructor(
        private val geoipFiles: GeoipFiles,
    ): ConfigCallback {

        public override fun TorConfig.BuilderScope.invoke(environment: TorRuntime.Environment) {
            val dataDirectory = configureFilesystem(environment)
            configureControlAuthentication(dataDirectory)
            ensureSocksPort()
            ensureControlPort(environment.workDirectory)
            configureDormancy()
            configureRuntimeRequired()
        }

        /**
         * Ensures that the following [TorOption] are present. If they
         * are not, then they are defined using tor's defaults and the
         * [TorRuntime.Environment].
         *
         *  - [TorOption.DataDirectory]
         *  - [TorOption.CacheDirectory]
         *  - [TorOption.ControlPortWriteToFile]
         *  - [TorOption.GeoIPFile]
         *  - [TorOption.GeoIPv6File]
         * */
        protected fun TorConfig.BuilderScope.configureFilesystem(environment: TorRuntime.Environment): File {
            @OptIn(InternalKmpTorApi::class)
            var dataDirectory = (this as RealBuilderScopeTorConfig).dataDirectoryOrNull

            if (dataDirectory == null) {
                dataDirectory = environment.workDirectory.resolve("data")
                TorOption.DataDirectory.configure(dataDirectory)
            }

            @OptIn(InternalKmpTorApi::class)
            if (!containsCacheDirectory) {
                TorOption.CacheDirectory.configure(environment.cacheDirectory)
            }

            @OptIn(InternalKmpTorApi::class)
            if (!containsControlPortWriteToFile) {
                val controlPortFile = environment.workDirectory.resolve("control.txt")
                TorOption.ControlPortWriteToFile.configure(controlPortFile)
            }

            TorOption.GeoIPFile.configure(geoipFiles.geoip)
            TorOption.GeoIPv6File.configure(geoipFiles.geoip6)

            return dataDirectory
        }

        /**
         * Ensures that controller authentication options are present.
         * If they are not, then [TorOption.CookieAuthentication] and
         * [TorOption.CookieAuthFile] are defined utilizing the
         * [TorOption.DataDirectory] location.
         * */
        protected fun TorConfig.BuilderScope.configureControlAuthentication(dataDirectory: File) {
            @OptIn(InternalKmpTorApi::class)
            when ((this as RealBuilderScopeTorConfig).cookieAuthenticationOrNull) {
                true -> {
                    // Enabled. Ensure CookieAuthFile is present.
                    if (!containsCookieAuthFile) {
                        TorOption.CookieAuthFile.configure(dataDirectory.resolve("control_auth_cookie"))
                    }
                }
                false -> {
                    // Disabled. Ensure CookieAuthFile is NOT
                    // present to simplify TorDaemon startup.
                    removeCookieAuthFile()
                }
                null -> {
                    // Undefined. If CookieAuthFile has been
                    // defined, enable CookieAuthentication.
                    if (containsCookieAuthFile) {
                        TorOption.CookieAuthentication.configure(true)
                    }
                }
            }

            // If no authentication methods have been defined, then
            // enable CookieAuthentication. This gives the option for
            // consumers to disable authentication by defining
            // `TorOption.CookieAuthentication.configure(false)`, which
            // will then roll unauthenticated (highly ill-advised).
            @OptIn(InternalKmpTorApi::class)
            if (cookieAuthenticationOrNull == null) {
                TorOption.CookieAuthentication.configure(true)
                if (!containsCookieAuthFile) {
                    TorOption.CookieAuthFile.configure(dataDirectory.resolve("control_auth_cookie"))
                }
            }
        }

        /**
         * If [TorOption.SocksPort] or [TorOption.__SocksPort] are
         * not defined, add [TorOption.__SocksPort] (with its default
         * of `9050`) so that it can be checked for availability on
         * the host and set to `auto`, if needed.
         *
         * This can be overridden by defining [TorOption.SocksPort]
         * or [TorOption.__SocksPort], and then assigning a value
         * of `false` for [BuilderScopePort.Socks.reassignable].
         *
         * e.g.
         *
         *     TorOption.__SocksPort.configure {
         *         // argument is already `9050`, tor's default
         *
         *         reassignable(false)
         *     }
         * */
        protected fun TorConfig.BuilderScope.ensureSocksPort() {
            @OptIn(InternalKmpTorApi::class)
            if ((this as RealBuilderScopeTorConfig).containsSocksPort) return

            TorOption.__SocksPort.configure { /* default 9050 */ }
        }

        /**
         * If [TorOption.ControlPort] or [TorOption.__ControlPort] have
         * not been defined, [TorOption.__ControlPort] is added. This will
         * always prefer configuring the option as a Unix Socket, if
         * available for the host and runtime environment.
         *
         * This can be overridden by defining [TorOption.ControlPort]
         * or [TorOption.__ControlPort].
         *
         * e.g.
         *
         *     TorOption.__ControlPort.configure {
         *         // argument is already set to `auto`
         *     }
         * */
        protected fun TorConfig.BuilderScope.ensureControlPort(workDirectory: File) {
            @OptIn(InternalKmpTorApi::class)
            if ((this as RealBuilderScopeTorConfig).containsControlPort) return

            TorOption.__ControlPort.configure {
                try {
                    // Prefer using Unix Sockets whenever possible
                    // because of things like airplane mode.
                    unixSocket(workDirectory.resolve("ctrl.sock"))
                } catch (_: UnsupportedOperationException) {
                    // Fallback to TCP port
                    auto()
                }
            }
        }

        /**
         * If [TorOption.DormantCanceledByStartup] is not defined, it is
         * added to the configuration with a value of `true`. The default
         * value for tor is `false`, but that is very problematic as the
         * [TorOption.DormantClientTimeout] default is `24 hours`. If the
         * application is running for that long in a dormant state and a
         * restart is performed (e.g. user closes app, opens it up again),
         * then the next startup will sit there in dormant mode. Not a
         * well-thought-out feature; this smooths it over while still
         * providing choice.
         *
         * This can be overridden by defining [TorOption.DormantCanceledByStartup].
         * */
        protected fun TorConfig.BuilderScope.configureDormancy() {
            @OptIn(InternalKmpTorApi::class)
            if ((this as RealBuilderScopeTorConfig).containsDormantCanceledByStartup) return
            TorOption.DormantCanceledByStartup.configure(true)
        }

        /**
         * Defines (and overrides if present) the following settings required for runtime
         * operations.
         *  - [TorOption.DisableNetwork]
         *  - [TorOption.RunAsDaemon]
         *  - [TorOption.__OwningControllerProcess]
         *  - [TorOption.__ReloadTorrcOnSIGHUP]
         *
         * @throws [UnsupportedOperationException] if on Java9+ and module 'java.management' is not present
         * */
        @Throws(UnsupportedOperationException::class)
        protected fun TorConfig.BuilderScope.configureRuntimeRequired() {
            TorOption.DisableNetwork.configure(true)
            TorOption.RunAsDaemon.configure(false)

            try {
                TorOption.__OwningControllerProcess.configure { /* default (current pid) */ }
            } finally {
                // Regardless of __OwningControllerProcess.configure throwing an exception,
                // configure this option as that is recoverable. Tor will simply be started
                // without it and notify the RuntimeEvent.ERROR observers.
                TorOption.__ReloadTorrcOnSIGHUP.configure(false)
            }
        }

        internal companion object {

            @JvmSynthetic
            @Throws(UnsupportedOperationException::class)
            internal fun apply(
                scope: TorConfig.BuilderScope,
                environment: TorRuntime.Environment,
                geoipFiles: GeoipFiles,
            ) {
                with(scope) {
                    with(Defaults(geoipFiles)) {
                        invoke(environment)
                    }
                }
            }
        }
    }
}
