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
package io.matthewnelson.kmp.tor.runtime.internal.observer

import io.matthewnelson.kmp.tor.runtime.TorListeners
import io.matthewnelson.kmp.tor.runtime.TorState
import io.matthewnelson.kmp.tor.runtime.core.OnEvent
import io.matthewnelson.kmp.tor.runtime.core.TorEvent

internal open class ObserverNotice internal constructor(
    private val manager: TorListeners.Manager,
    staticTag: String,
): TorEvent.Observer(
    TorEvent.NOTICE,
    staticTag,
    OnEvent.Executor.Immediate,
    OnEvent.noOp()
) {

    protected override fun notify(data: String) {
        with(data) {
            when {
                startsWith(BOOTSTRAPPED) -> parseBootstrapped()
                startsWith(CLOSING) -> parseListenerClosing()
                startsWith(OPENED) -> parseListenerOpened()
            }
        }
    }

    // Bootstrapped 0%
    private fun String.parseBootstrapped() {
        val pct = substringAfter(BOOTSTRAPPED, "")
            .substringBefore('%', "")
            .toByteOrNull()
            ?: return

        manager.update(TorState.Daemon.On(pct))
    }

    // Closing no-longer-configured DNS listener on 127.0.0.1:53085
    // Closing no-longer-configured HTTP tunnel listener on 127.0.0.1:48932
    // Closing no-longer-configured Socks listener on 127.0.0.1:9150
    // Closing no-longer-configured Transparent pf/netfilter listener on 127.0.0.1:45963
    // Closing partially-constructed Socks listener connection (ready) on /tmp/kmp_tor_test/obs_conn_no_net/work/socks5.sock
    //
    // UnixDomainSocket
    // Closing no-longer-configured Socks listener on ???:0
    private fun String.parseListenerClosing() {
        val trimmed = substringAfter(CLOSING, "")
            .substringAfter(' ', "")
            .ifEmpty { return }

        val type = trimmed
            .substringBefore(" listener ", "")
            .ifEmpty { return }

        val address = trimmed
            .substringAfter(CONN_READY_ON, "")
            .ifEmpty { trimmed.substringAfter(LISTENER_ON, "") }
            .ifEmpty { return }

        manager.update(type, address, wasClosed = true)
    }

    // Opened DNS listener connection (ready) on 127.0.0.1:34841
    // Opened HTTP tunnel listener connection (ready) on 127.0.0.1:46779
    // Opened Socks listener connection (ready) on 127.0.0.1:36237
    // Opened Socks listener connection (ready) on /tmp/kmp_tor_test/sf_restart/work/socks.sock
    // Opened Transparent pf/netfilter listener connection (ready) on 127.0.0.1:37527
    // Opened Transparent natd listener connection (ready) on 127.0.0.1:9060
    // Opened Metrics listener connection (ready) on 127.0.0.1:9059
    // Opened OR listener connection (ready) on 0.0.0.0:9061
    // Opened Extended OR listener connection (ready) on 127.0.0.1:9058
    // Opened Directory listener connection (ready) on 0.0.0.0:9057
    private fun String.parseListenerOpened() {
        val iOpened = indexOf(OPENED)
        val iReady = indexOf(CONN_READY_ON)
        if (iOpened == -1 || iReady == -1) return

        val type = substring(iOpened + OPENED.length, iReady)
        val address = substring(iReady + CONN_READY_ON.length, length)

        manager.update(type, address, wasClosed = false)
    }

    private companion object {
        private const val BOOTSTRAPPED = "Bootstrapped "

        private const val CLOSING = "Closing "
        private const val OPENED = "Opened "

        private const val CONN_READY_ON = " listener connection (ready) on "
        private const val LISTENER_ON = " listener on "
    }
}
