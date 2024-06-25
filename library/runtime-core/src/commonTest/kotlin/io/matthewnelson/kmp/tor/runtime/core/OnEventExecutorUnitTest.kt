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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package io.matthewnelson.kmp.tor.runtime.core

import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.*

expect class OnEventExecutorUnitTest(): OnEventExecutorBaseTest

@OptIn(InternalKmpTorApi::class)
abstract class OnEventExecutorBaseTest {

    // Jvm and non-darwin native targets should be false
    // b/c only core dependency only in runtime-core
    protected abstract val expectedIsAvailable: Boolean
    protected open val isMainActuallyImmediate: Boolean = false

    @Test
    fun givenMain_whenIsAvailable_thenIsAsExpected() {
        assertEquals(expectedIsAvailable, OnEvent.Executor.Main.isAvailable)
    }

    @Test
    fun givenMain_whenIsAvailable_thenExecutes() = runTest {
        if (!OnEvent.Executor.Main.isAvailable) {
            println("Skipping...")
            return@runTest
        }

        val job = Job(currentCoroutineContext().job)
        OnEvent.Executor.Main.execute(EmptyCoroutineContext) { job.complete() }

        // Node.js uses immediate implementation b/c the entire
        // kmp-tor implementation is asynchronous and uses
        // Dispatchers.Main
        if (isMainActuallyImmediate) {
            assertFalse(job.isActive)
        } else {
            job.join()
            assertTrue(job.isCompleted)
        }
    }

    @Test
    fun givenMain_whenIsNotAvailable_thenThrowsException() {
        if (OnEvent.Executor.Main.isAvailable) {
            println("Skipping...")
            return
        }

        // Could be IllegalStateException (jvm) or NotImplementedError (native)
        assertFails {
            OnEvent.Executor.Main.execute(EmptyCoroutineContext) {}
        }
    }

    @Test
    fun givenImmediate_whenExecute_thenExecutes() {
        var invocationExecute = false
        OnEvent.Executor.Immediate.execute(EmptyCoroutineContext) { invocationExecute = true }
        assertTrue(invocationExecute)
    }
}
