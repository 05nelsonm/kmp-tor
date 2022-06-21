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
package io.matthewnelson.kmp.tor.helper

import io.matthewnelson.kmp.tor.KmpTorLoaderJvm
import io.matthewnelson.kmp.tor.PlatformInstaller
import io.matthewnelson.kmp.tor.PlatformInstaller.InstallOption
import io.matthewnelson.kmp.tor.TorConfigProviderJvm
import io.matthewnelson.kmp.tor.common.address.Port
import io.matthewnelson.kmp.tor.common.annotation.InternalTorApi
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Setting.*
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Option.*
import io.matthewnelson.kmp.tor.controller.common.events.TorEvent
import io.matthewnelson.kmp.tor.controller.common.file.Path
import io.matthewnelson.kmp.tor.controller.common.internal.ControllerUtils
import io.matthewnelson.kmp.tor.manager.TorConfigProvider
import io.matthewnelson.kmp.tor.manager.TorManager
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent
import io.matthewnelson.kmp.tor.manager.common.state.isOff
import kotlinx.coroutines.*
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.*
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.Executors

@OptIn(ExperimentalCoroutinesApi::class, InternalTorApi::class)
abstract class TorTestHelper {

    companion object {

        @get:JvmStatic
        @get:ClassRule
        val testDir: TemporaryFolder = TemporaryFolder()

        private var dispatcher: CoroutineDispatcher? = null
        private lateinit var tmpWorkDir: File
        private lateinit var tmpCacheDir: File
        private var sManager: TorManager? = null
        private var sProvider: TestTorConfigProvider? = null

        @JvmStatic
        @BeforeClass
        fun beforeClassHelper() {
            dispatcher = Executors
                .newSingleThreadExecutor()
                .asCoroutineDispatcher()
                .also { Dispatchers.setMain(it) }

            tmpWorkDir = testDir.newFolder("tor service")
            tmpCacheDir = testDir.newFolder("cache")
        }

        @JvmStatic
        @AfterClass
        fun afterClassHelper() {
            sManager?.let {
                runBlocking {
                    it.stop()
                    it.destroy()
                    delay(250L)
                }
            }
            sProvider = null
            sManager = null
            Dispatchers.resetMain()
            (dispatcher as ExecutorCoroutineDispatcher).close()
            dispatcher = null
        }
    }

    protected open fun testConfig(testProvider: TorConfigProviderJvm): TorConfig {
        return TorConfig.Builder {
            put(Ports.Control().set(AorDorPort.Auto))
            put(Ports.Socks().set(AorDorPort.Auto))
            put(Ports.HttpTunnel().set(AorDorPort.Auto))
            put(Ports.Trans().set(AorDorPort.Auto))

            put(UnixSockets.Control().set(FileSystemFile(
                testProvider.workDir.builder {
                    addSegment(DataDirectory.DEFAULT_NAME)
                    addSegment(UnixSockets.Control.DEFAULT_NAME)
                }
            )))

            put(UnixSockets.Socks().set(FileSystemFile(
                testProvider.workDir.builder {
                    addSegment(DataDirectory.DEFAULT_NAME)
                    addSegment(UnixSockets.Socks.DEFAULT_NAME)
                }
            )))

            val hsPath = testProvider.workDir.builder {
                addSegment(HiddenService.DEFAULT_PARENT_DIR_NAME)
                addSegment("test_service")
            }

            put(HiddenService()
                .setPorts(ports = setOf(
                    HiddenService.UnixSocket(virtualPort = Port(1024), targetUnixSocket = hsPath.builder {
                        addSegment(HiddenService.UnixSocket.DEFAULT_UNIX_SOCKET_NAME)
                    }),
                    HiddenService.Ports(virtualPort = Port(1025), targetPort = Port(1027)),
                    HiddenService.Ports(virtualPort = Port(1026), targetPort = Port(1027)),
                ))
                .setMaxStreams(maxStreams = HiddenService.MaxStreams(value = 2))
                .setMaxStreamsCloseCircuit(value = TorF.True)
                .set(FileSystemDir(hsPath))
            )

            put(HiddenService()
                .setPorts(ports = setOf(
                    HiddenService.Ports(virtualPort = Port(1028), targetPort = Port(1030)),
                    HiddenService.Ports(virtualPort = Port(1029), targetPort = Port(1030))
                ))
                .set(FileSystemDir(
                    testProvider.workDir.builder {
                        addSegment(HiddenService.DEFAULT_PARENT_DIR_NAME)
                        addSegment("test_service_2")
                    }
                ))
            )

            put(DormantClientTimeout().set(Time.Minutes(10)))
            put(DormantCanceledByStartup().set(TorF.True))

            put(ClientOnionAuthDir().set(FileSystemDir(
                testProvider.workDir.builder { addSegment(ClientOnionAuthDir.DEFAULT_NAME) }
            )))
        }.build()
    }

    private class TestTorConfigProvider(
        private val provideConfig: (TestTorConfigProvider) -> TorConfig
    ): TorConfigProviderJvm() {
        override val workDir: Path get() = Path(tmpWorkDir)
        override val cacheDir: Path get() = Path(tmpCacheDir)
        override fun provide(): TorConfig {
            return provideConfig.invoke(this)
        }
    }

    protected val configProvider: TorConfigProviderJvm
        get() = sProvider ?: TestTorConfigProvider { testConfig(it) }.also { sProvider = it }

    protected val manager: TorManager
        get() {
            return sManager?.let {
                if (it.isDestroyed) {
                    ensureStarted()
                } else {
                    it
                }
            } ?: ensureStarted()
        }

    @Before
    fun beforeHelper() = runBlocking {
        ensureStarted()
        delay(25L)
    }

    private fun ensureStarted(): TorManager {
        sManager?.let {
            if (it.isDestroyed) {
                sManager = null
            } else {
                it.startQuietly()
                return it
            }
        }

        val installOption: InstallOption = InstallOption.CleanInstallIfMissing

        val installer: PlatformInstaller = when {
            ControllerUtils.isMingw -> {
                println("\nRunning tests for Windows\n")
                PlatformInstaller.mingwX64(installOption)
            }
            ControllerUtils.isDarwin -> {
                println("\nRunning tests for Darwin\n")
                PlatformInstaller.macosX64(installOption)
            }
            ControllerUtils.isLinux -> {
                println("\nRunning tests for Linux\n")
                PlatformInstaller.linuxX64(installOption)
            }
            else -> throw AssertionError("Failed to determine Operating System")
        }

        val loader = KmpTorLoaderJvm(installer, configProvider)

        val manager = TorManager.newInstance(loader)
        manager.debug(true)
        manager.addListener(object : TorManagerEvent.SealedListener {
            override fun onEvent(event: TorManagerEvent) {
                if (event is TorManagerEvent.Log.Error) {
                    event.value.printStackTrace()
                } else {
                    println(event.toString())
                }
            }

            override fun onEvent(event: TorEvent.Type.SingleLineEvent, output: String) {}

            override fun onEvent(
                event: TorEvent.Type.MultiLineEvent,
                output: List<String>
            ) {}

        })
        manager.startQuietly()

        sManager = manager
        return manager
    }

    protected suspend fun awaitLastValidatedTorConfig(): TorConfigProvider.ValidatedTorConfig {
        var count = 0
        while (count < 10) {
            configProvider.lastValidatedTorConfig?.let { return it }
            delay(50L)
            count++
        }

        throw AssertionError("Failed to retrieve LastValidatedTorConfig")
    }

    protected suspend fun awaitBootstrap(timeout: Long = 180_000L) {
        val timeoutTime = System.currentTimeMillis() + timeout
        println("==== Awaiting Bootstrap")

        while (!manager.state.isBootstrapped) {
            delay(1_000L)

            if (System.currentTimeMillis() > timeoutTime) {
                throw AssertionError("await bootstrap timed out after ${timeout}ms")
            }

            if (manager.state.isOff()) {
                throw AssertionError("TorManager is off")
            }
        }
    }
}
