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
package io.matthewnelson.kmp.tor.runtime.test

import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.SysTempDir
import io.matthewnelson.kmp.file.path
import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.tor.common.api.ExperimentalKmpTorApi
import io.matthewnelson.kmp.tor.common.api.InternalKmpTorApi
import io.matthewnelson.kmp.tor.common.api.ResourceLoader
import io.matthewnelson.kmp.tor.common.core.SynchronizedObject
import io.matthewnelson.kmp.tor.common.core.synchronized
import io.matthewnelson.kmp.tor.runtime.*
import io.matthewnelson.kmp.tor.runtime.Action.Companion.stopDaemonAsync
import io.matthewnelson.kmp.tor.runtime.FileID.Companion.fidEllipses
import io.matthewnelson.kmp.tor.runtime.core.*
import io.matthewnelson.kmp.tor.runtime.core.config.TorConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

private val TEST_DIR = SysTempDir.resolve("kmp_tor_runtime_test")

internal expect fun filesystem(): FileSystem
internal expect fun testLoader(dir: File): ResourceLoader.Tor

private val TEST_ENV: TorRuntime.Environment by lazy {
    TorRuntime.Environment.Builder(
        workDirectory = TEST_DIR.resolve("work"),
        cacheDirectory = TEST_DIR.resolve("cache"),
        loader = ::testLoader,
        block = {
            defaultEventExecutor = OnEvent.Executor.Immediate

            @OptIn(ExperimentalKmpTorApi::class)
            serviceFactoryLoader = object : TorRuntime.ServiceFactory.Loader() {
                override fun loadProtected(initializer: TorRuntime.ServiceFactory.Initializer): TorRuntime.ServiceFactory {
                    return TestServiceFactory(initializer)
                }
            }

            resourceDir = TEST_DIR.resolve("resources")
        }
    )
}

private val LOGS = mutableListOf<String>()

@OptIn(InternalKmpTorApi::class)
private val LOGS_LOCK = SynchronizedObject()

@OptIn(InternalKmpTorApi::class)
private object TestConfig: ConfigCallback {

    private val lock = SynchronizedObject()
    private val configs = mutableListOf<ConfigCallback>()

    fun add(config: ConfigCallback) {
        synchronized(lock) { configs.add(config) }
    }

    fun clear() {
        synchronized(lock) { configs.clear() }
    }

    override fun TorConfig.BuilderScope.invoke(environment: TorRuntime.Environment) {
        synchronized(lock) {
            configs.forEach { config -> with(config) { invoke(environment) } }
        }
    }
}

private val TEST_RUNTIME: TestServiceFactory by lazy {
    TorRuntime.Builder(TEST_ENV) {
        required(TorEvent.ADDRMAP)
        required(TorEvent.ERR)
        required(TorEvent.WARN)

        config(TestConfig)

        RuntimeEvent.entries().forEach { event ->
            when (event) {
                is RuntimeEvent.ERROR -> observerStatic(event) { t ->
                    if (t is UncaughtException) {
                        throw t
                    }

                    @OptIn(InternalKmpTorApi::class)
                    synchronized(LOGS_LOCK) { LOGS.add(t.toString()) }
                }
                else -> observerStatic(event, OnEvent.Executor.Immediate) { data ->
                    @OptIn(InternalKmpTorApi::class)
                    synchronized(LOGS_LOCK) { LOGS.add(data.toString()) }
                }
            }
        }
        TorEvent.entries().forEach { event ->
            observerStatic(event, OnEvent.Executor.Immediate) { data ->
                @OptIn(InternalKmpTorApi::class)
                synchronized(LOGS_LOCK) { LOGS.add(data) }
            }
        }
    } as TestServiceFactory
}

private val TEST_LOCK = Mutex()

internal fun runTorTest(
    context: CoroutineContext = EmptyCoroutineContext,
    timeout: Duration = 5.minutes,
    config: ConfigCallback? = null,
    testBody: suspend TestScope.(runtime: TestServiceFactory) -> Unit,
): TestResult = runTest(context, timeout) {
    TEST_LOCK.withLock {
        TEST_RUNTIME.stopDaemonAsync()

        @OptIn(InternalKmpTorApi::class)
        synchronized(LOGS_LOCK) { LOGS.clear() }

        @OptIn(ExperimentalKmpTorApi::class)
        TEST_RUNTIME.serviceStart = null
        if (config != null) TestConfig.add(config)
        TEST_RUNTIME.environment().debug = true

        var threw: Throwable? = null
        try {
            testBody(TEST_RUNTIME)
        } catch (t: Throwable) {
            threw = t
        }

        try {
            TEST_RUNTIME.stopDaemonAsync()
        } catch (_: Throwable) {}

        TEST_RUNTIME.clearObservers()

        @OptIn(ExperimentalKmpTorApi::class)
        TEST_RUNTIME.serviceStart = null
        TestConfig.clear()

        try {
            filesystem().deleteRecursively(TEST_DIR.path.toPath())
        } catch (_: Throwable) {}

        if (threw == null) return@withLock

        @OptIn(InternalKmpTorApi::class)
        val logs = synchronized(LOGS_LOCK) { LOGS.joinToString(separator = "\n") }
        threw.addSuppressed(Error("LOGS: \n$logs"))
        throw threw
    }
}

fun List<Lifecycle.Event>.assertLCEsContain(
    className: String,
    name: Lifecycle.Event.Name,
    fid: FileID? = null,
) {
    for (lce in this) {
        if (lce.className != className) continue
        if (fid != null) {
            if (lce.fid != fid.fidEllipses) continue
        }

        if (lce.name == name) return
    }

    fail("LCEs did not contain $name for $className${fid?.let { "[fid=${it.fidEllipses}]" } ?: ""}")
}

fun List<Lifecycle.Event>.assertLCEsDoNotContain(
    className: String,
    name: Lifecycle.Event.Name,
    fid: FileID? = null,
) {
    var error: AssertionError? = null
    try {
        assertLCEsContain(className, name, fid)
        error = AssertionError("LCEs contained $name for $className${fid?.let { "[fid=${it.fidEllipses}]" } ?: ""}")
    } catch (_: AssertionError) {
        // pass
    }

    error?.let { throw it }
}
