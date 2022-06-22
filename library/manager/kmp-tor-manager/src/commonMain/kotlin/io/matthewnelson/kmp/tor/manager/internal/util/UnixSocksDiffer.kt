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

import io.matthewnelson.kmp.tor.controller.common.config.TorConfig
import io.matthewnelson.kmp.tor.controller.common.file.Path
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent.AddressInfo
import io.matthewnelson.kmp.tor.manager.internal.ext.unixSocksClosed
import io.matthewnelson.kmp.tor.manager.internal.ext.unixSocksOpened
import io.matthewnelson.kmp.tor.manager.internal.util.synchronous.SynchronizedMutableSet
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * This helper class handles managing of closing/opening of unix SocksPort
 * events on config changes. If there was a previously set unix SocksPort
 * opened and configSet/configReset was called which modified the unix
 * SocksPort, we are left in an awkward state because:
 *
 *  - The order in which Tor dispatches those events
 *  - Inability to compare with current [AddressInfo.unixSocks] because
 *    the closing notice does not contain the path (it dispatches ???:0)
 *
 * Tor will dispatch events in the following order:
 *
 *  1st - unix Socks listener(s) opened
 *  2nd - unix Socks listener(s) closed
 *  3rd - config changed SocksPort=unix:/path/to/socks.sock
 *
 * This just keeps track of what was opened, what was confirmed
 * via the config change, and then removes the unconfirmed.
 * */
internal class UnixSocksDiffer(private val torManagerScope: CoroutineScope, private val handler: AddressInfoHandler) {

    private val pathsToRemove = SynchronizedMutableSet<Path>()
    private val closingJob: AtomicRef<Job?> = atomic(null)

    @JvmSynthetic
    internal fun onOpened(address: String) {
        pathsToRemove.withLock {
            handler.addressInfo.unixSocksOpened(address)?.let { info ->
                info.unixSocks?.let { paths -> addAll(paths) }
                handler.dispatchNewAddressInfo(info)
            }
        }
    }

    @JvmSynthetic
    internal fun onClosed() {
        closingJob.update { job ->
            job?.cancel()

            torManagerScope.launch {
                delay(50L)
                pathsToRemove.withLock {
                    for (toRemove in this) {
                        handler.addressInfo.unixSocksClosed(toRemove.value)?.let { info ->
                            handler.dispatchNewAddressInfo(info)
                        }
                    }

                    clear()
                }
            }
        }
    }

    @JvmSynthetic
    internal fun onConfChanged(output: String) {
        // TODO: Issue #157 use TorConfig.KeyWord
        if (!output.startsWith(SOCKS_PORT)) return

        pathsToRemove.withLock {
            val value = output.substringAfter(SOCKS_PORT)

            if (value == TorConfig.Option.AorDorPort.Disable.value) {
                // If SocksPort has been disabled, we need to add all
                // current paths to mark them for removal
                handler.addressInfo.unixSocks?.let { paths -> addAll(paths) }
                return@withLock
            }

            if (value.startsWith(UNIX)) {
                // SocksPort=unix:"/tmp/junit15600960770888728454/tor service/data/socks_test_set.sock" CacheDNS OnionTrafficOnly IsolateSOCKSAuth
                val address = value
                    .substringAfter(UNIX)
                    .substringBefore('"')

                remove(Path(address))
            }
        }
    }

    companion object {
        private const val SOCKS_PORT = "SocksPort="
        private const val UNIX = "unix:\""
    }
}
