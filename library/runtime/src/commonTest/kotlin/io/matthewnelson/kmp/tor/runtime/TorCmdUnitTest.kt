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

import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.resource.SynchronizedObject
import io.matthewnelson.kmp.tor.core.resource.synchronized
import io.matthewnelson.kmp.tor.runtime.Action.Companion.startDaemonAsync
import io.matthewnelson.kmp.tor.runtime.core.OnFailure
import io.matthewnelson.kmp.tor.runtime.core.OnSuccess
import io.matthewnelson.kmp.tor.runtime.core.TorConfig
import io.matthewnelson.kmp.tor.runtime.core.ctrl.ConfigEntry
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.core.util.executeAsync
import io.matthewnelson.kmp.tor.runtime.test.TestUtils.ensureStoppedOnTestCompletion
import io.matthewnelson.kmp.tor.runtime.test.TestUtils.testEnv
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(InternalKmpTorApi::class)
class TorCmdUnitTest {

    @Test
    fun givenConfigGet_whenKeyword_thenIsRecognizedByTor() = runTest {
        val runtime = TorRuntime.Builder(testEnv("cmd_getconf_test")) {
//            observerStatic(RuntimeEvent.LOG.DEBUG) { println(it) }
        }.ensureStoppedOnTestCompletion()

        runtime.startDaemonAsync()

        val failures = mutableListOf<Throwable>()
        val lock = SynchronizedObject()

        val onFailure = OnFailure { synchronized(lock) { failures.add(it) } }

        KEYWORDS.forEach { kw ->
            runtime.enqueue(TorCmd.Config.Get(kw), onFailure, OnSuccess.noOp())
        }

        // will suspend test until all previously enqueued jobs complete
        val result = runtime.executeAsync(TorCmd.Config.Get(
            TorConfig.ConnectionPadding,
            TorConfig.DataDirectory,
        ))

        synchronized(lock) {
            var e: AssertionError? = null

            failures.forEach { f ->
                if (e == null) {
                    e = AssertionError("Config.Get failures")
                }

                e!!.addSuppressed(f)
            }

            e?.let { err -> throw err }
        }

        assertEquals(2, result.size)
    }

    private companion object {

        val KEYWORDS = listOf(
            TorConfig.__ControlPort,
            TorConfig.__DNSPort,
            TorConfig.__HTTPTunnelPort,
            TorConfig.__OwningControllerProcess,
            TorConfig.__SocksPort,
            TorConfig.__TransPort,
            TorConfig.AutomapHostsOnResolve,
            TorConfig.AutomapHostsSuffixes,
            TorConfig.CacheDirectory,
            TorConfig.ClientOnionAuthDir,
            TorConfig.ConnectionPadding,
            TorConfig.ControlPortWriteToFile,
            TorConfig.CookieAuthentication,
            TorConfig.CookieAuthFile,
            TorConfig.DataDirectory,
            TorConfig.DisableNetwork,
            TorConfig.DormantCanceledByStartup,
            TorConfig.DormantClientTimeout,
            TorConfig.DormantOnFirstStartup,
            TorConfig.DormantTimeoutDisabledByIdleStreams,
            TorConfig.GeoIPExcludeUnknown,
            TorConfig.GeoIPFile,
            TorConfig.GeoIPv6File,
            TorConfig.HiddenServiceDir,
            TorConfig.HiddenServicePort,
            TorConfig.HiddenServiceVersion,
            TorConfig.HiddenServiceAllowUnknownPorts,
            TorConfig.HiddenServiceMaxStreams,
            TorConfig.HiddenServiceMaxStreamsCloseCircuit,
            TorConfig.HiddenServiceDirGroupReadable,
            TorConfig.HiddenServiceNumIntroductionPoints,
            TorConfig.RunAsDaemon,
            TorConfig.SyslogIdentityTag,
            TorConfig.AndroidIdentityTag,
            TorConfig.VirtualAddrNetworkIPv4,
            TorConfig.VirtualAddrNetworkIPv6,
        )
    }
}
