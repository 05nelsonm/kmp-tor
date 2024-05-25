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
package io.matthewnelson.kmp.tor.runtime.internal

import io.matthewnelson.kmp.tor.runtime.TorListeners
import io.matthewnelson.kmp.tor.runtime.TorState
import io.matthewnelson.kmp.tor.runtime.core.EnqueuedJob
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.ctrl.TorCmdInterceptor

internal open class ObserverLogProcess internal constructor(
    private val manager: TorListeners.Manager,
) {

    // Is registered via RealTorRuntime.factory
    internal val newNymInterceptor = TorCmdInterceptor.intercept<TorCmd.Signal.NewNym> { job, cmd ->
        job.onNewNymJob()
        cmd
    }

    protected fun EnqueuedJob.onNewNymJob() {
        invokeOnCompletion {
            if (isError) return@invokeOnCompletion
            // TODO: Listen for rate-limit notice
        }
    }

    protected open fun notify(line: String) {
        val notice = line
            .substringAfter(NOTICE, "")
            .ifBlank { return }

        with(notice) {
            when {
                startsWith(BOOTSTRAPPED) -> parseBootstrapped()
                startsWith(CLOSING) -> parseListenerClosing()
                startsWith(OPENED) -> parseListenerOpened()
            }
        }
    }

    // [notice] Bootstrapped 0%
    private fun String.parseBootstrapped() {
        val pct = substringAfter(BOOTSTRAPPED, "")
            .substringBefore('%', "")
            .toByteOrNull()
            ?: return

        manager.update(TorState.Daemon.On(pct))
    }

    // [notice] Closing no-longer-configured DNS listener on 127.0.0.1:53085
    // [notice] Closing no-longer-configured HTTP tunnel listener on 127.0.0.1:48932
    // [notice] Closing no-longer-configured Socks listener on 127.0.0.1:9150
    // [notice] Closing no-longer-configured Transparent pf/netfilter listener on 127.0.0.1:45963
    //
    // UnixDomainSocket
    // [notice] Closing no-longer-configured Socks listener on ???:0
    private fun String.parseListenerClosing() {
        val type = substringAfter(NO_LONGER_CONFIGURED, "")
            .substringBefore(' ', "")
            .trim()
        val address = substringAfter(LISTENER_ON, "")
            .trim()

        manager.update(type, address, wasClosed = true)
    }

    // [notice] Opened DNS listener connection (ready) on 127.0.0.1:34841
    // [notice] Opened HTTP tunnel listener connection (ready) on 127.0.0.1:46779
    // [notice] Opened Socks listener connection (ready) on 127.0.0.1:36237
    // [notice] Opened Socks listener connection (ready) on /tmp/kmp_tor_test/sf_restart/work/socks.sock
    // [notice] Opened Transparent pf/netfilter listener connection (ready) on 127.0.0.1:37527
    private fun String.parseListenerOpened() {
        val type = substringAfter(OPENED, "")
            .substringBefore(' ', "")
            .trim()
        val address = substringAfter(CONN_READY_ON, "")
            .trim()

        manager.update(type, address, wasClosed = false)
    }

    private companion object {
        private const val NOTICE = " [notice] "

        private const val BOOTSTRAPPED = "Bootstrapped "

        private const val CLOSING = "Closing "
        private const val NO_LONGER_CONFIGURED = " no-longer-configured "
        private const val LISTENER_ON = " listener on "

        private const val OPENED = "Opened "
        private const val CONN_READY_ON = " listener connection (ready) on "
    }
}
