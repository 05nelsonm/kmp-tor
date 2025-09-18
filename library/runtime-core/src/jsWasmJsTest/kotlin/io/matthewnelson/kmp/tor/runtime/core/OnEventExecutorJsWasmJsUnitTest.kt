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

import io.matthewnelson.kmp.tor.common.api.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.core.internal.isImmediate
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertTrue

actual class OnEventExecutorUnitTest: OnEventExecutorBaseTest() {
    override val expectedIsAvailable: Boolean = true
    override val isMainActuallyImmediate: Boolean = true

    @Test
    fun givenMain_whenIsImmediate_thenIsTrue() {
        assertTrue(OnEvent.Executor.Main.isImmediate())

        var wasInvoked = false

        @OptIn(InternalKmpTorApi::class)
        OnEvent.Executor.Main.execute(EmptyCoroutineContext) { wasInvoked = true }

        // Actual implementation of MainExecutorInternal is typealias to Immediate. If execution
        // were not to have been immediate (a coroutine was launched), then this would be false
        assertTrue(wasInvoked)
    }
}
