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
package io.matthewnelson.kmp.tor.runtime.core.config

import kotlin.reflect.full.declaredFunctions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Only reflection based tests on Jvm. All others
 * are performed in [TorOptionUnitTest].
 * */
class TorOptionJvmUnitTest {

    @Test
    fun givenOptions_whenAllFoundViaReflection_thenTorOptionEntriesContainsAll() {
        val entriesReflect = mutableListOf<TorOption>()

        for (clazz in TorOption::class.nestedClasses) {
            if (clazz.isCompanion) continue
            if (!clazz.isFinal) continue

            val instance = clazz.objectInstance ?: continue
            if (instance !is TorOption) continue

            entriesReflect.add(instance)
        }

        // Force update expected test value when something is added.
        val expected = 358
        assertEquals(expected, TorOption.entries.size)
        assertEquals(expected, entriesReflect.size)
        assertEquals(expected, entriesReflect.toSet().size)

        assertTrue(TorOption.entries.containsAll(entriesReflect))

        entriesReflect.forEach { reflect ->
            val entry = TorOption.valueOf(reflect.name)
            assertEquals(entry, reflect)
        }
    }

    @Test
    fun givenOptions_whenHasContract_thenContainsAsSettingFunction() {
        val contracts = TorOption.entries.filterIsInstance<ConfigurableContract<*>>()

        // Force update expected test value when something is added.
        assertEquals(165, contracts.size)

        contracts.forEach { instance ->
            val asSetting = instance::class.declaredFunctions.filter { it.name == "asSetting" }
            assertEquals(1, asSetting.size)
        }
    }
}
