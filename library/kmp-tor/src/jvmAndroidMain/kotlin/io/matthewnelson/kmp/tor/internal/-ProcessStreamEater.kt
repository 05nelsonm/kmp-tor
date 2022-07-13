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
package io.matthewnelson.kmp.tor.internal

import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent
import io.matthewnelson.kmp.tor.manager.common.exceptions.TorManagerException
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.util.*
import java.util.concurrent.Executors

@Suppress("BlockingMethodInNonBlockingContext")
internal class ProcessStreamEater(
    parentJob: Job,
    input: BufferedReader,
    error: BufferedReader,
    notify: (TorManagerEvent.Log) -> Unit
) {

    private val supervisor = SupervisorJob(parentJob)
    private val dispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
    private val scope = CoroutineScope(supervisor + dispatcher)

    init {
        scope.launch(CoroutineName("Process.inputStream Reader")) {
            notify.invoke(TorManagerEvent.Log.Debug("Reading Process.inputStream"))
            var inputScan: Scanner? = null

            try {
                inputScan = Scanner(input)

                while (currentCoroutineContext().isActive && inputScan.hasNextLine()) {
                    val line = inputScan.nextLine()
                    when {
                        line.contains(ERROR) -> {
                            notify.invoke(TorManagerEvent.Log.Error(TorManagerException(line)))
                        }
                        line.contains(NOTICE) -> {
                            // pipe notices to debug as we are already listening for them
                            // via the control port by default.
                            notify.invoke(TorManagerEvent.Log.Debug(line))
                        }
                        line.contains(WARN) -> {
                            notify.invoke(TorManagerEvent.Log.Warn(line))
                        }
                        else -> {
                            notify.invoke(TorManagerEvent.Log.Info(line))
                        }
                    }
                }
            } catch (e: Exception) {
                notify.invoke(TorManagerEvent.Log.Error(e))
            } finally {
                notify.invoke(TorManagerEvent.Log.Debug("Stopped reading Process.inputStream"))
                try {
                    input.close()
                } catch (_: Exception) {}
                try {
                    inputScan?.close()
                } catch (_: Exception) {}
            }
        }

        scope.launch(CoroutineName("Process.errorStream Reader")) {
            notify.invoke(TorManagerEvent.Log.Debug("Reading Process.errorStream"))
            var errorScan: Scanner? = null

            try {
                errorScan = Scanner(error)

                while (currentCoroutineContext().isActive && errorScan.hasNextLine()) {
                    notify.invoke(TorManagerEvent.Log.Error(
                        TorManagerException(errorScan.nextLine())
                    ))
                }
            } catch (e: Exception) {
                notify.invoke(TorManagerEvent.Log.Error(e))
            } finally {
                notify.invoke(TorManagerEvent.Log.Debug("Stopped reading Process.errorStream"))
                try {
                    error.close()
                } catch (_: Exception) {}
                try {
                    errorScan?.close()
                } catch (_: Exception) {}
            }
        }

        supervisor.invokeOnCompletion {
            dispatcher.close()
        }
    }

    companion object {
        private const val ERROR = " [err] "
        private const val NOTICE = " [notice] "
        private const val WARN = " [warn] "
    }
}
