/*
 * Copyright (c) 2021 Matthew Nelson
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
package io.matthewnelson.kmp.tor.controller.internal.coroutines

import java.util.concurrent.ExecutorService
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.isActive
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class JvmTorCoroutineManagerUnitTest {

    private lateinit var manager: TorCoroutineManager // RealTorCoroutineManager

    @Before
    fun before() {
        manager = TorCoroutineManager.newInstance()
    }

    @After
    fun after() {
        manager.close()
    }

    @Test
    fun givenClosedTorCoroutineManager_whenScopeRetrieved_resultsInNull() {
        Assert.assertFalse(manager.isClosed)

        val dispatcher = manager.dispatcher()
        Assert.assertFalse(manager.isClosed)
        manager.close()
        Assert.assertTrue(manager.isClosed)
        Assert.assertTrue(
            ((dispatcher as ExecutorCoroutineDispatcher).executor as ExecutorService).isShutdown
        )

        val torScope = manager.torScope()
        Assert.assertNull(torScope)
    }

    @Test
    fun givenScopeRetrieval_whenScopeNameRetrieved_digitIncrements() {
        val torScope = manager.torScope() ?: throw AssertionError("something is very wrong...")

        // count should be the same for the scope name
        val countInitial = torScope.coroutineContext[CoroutineName]!!.name.last()
        val countBefore = manager.torScope()!!.coroutineContext[CoroutineName]!!.name.last()

        Assert.assertEquals(countInitial, countBefore)

        // closing cancels supervisor job and closes dispatcher
        manager.close()
        Assert.assertFalse(torScope.isActive)
        Assert.assertTrue(manager.isClosed)
        Assert.assertNull(manager.dispatcher())

        // new count issued when retrieving scope
        manager = TorCoroutineManager.newInstance()
        val scopeAfter = manager.torScope() ?: throw AssertionError("something is very wrong...")

        val countAfter = scopeAfter.coroutineContext[CoroutineName]!!.name.last()
        Assert.assertNotEquals(torScope, scopeAfter)
        Assert.assertNotEquals(countBefore, countAfter)
    }
}
