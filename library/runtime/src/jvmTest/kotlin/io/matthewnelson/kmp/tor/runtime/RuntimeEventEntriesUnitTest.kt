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
package io.matthewnelson.kmp.tor.runtime

import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ensure that [RuntimeEvent.entries] contains all
 * instances of [RuntimeEvent] via reflection.
 * */
class RuntimeEventEntriesUnitTest {

    @Test
    fun givenRuntimeEventInstances_whenEntriesList_thenContainsAllEventTypes() {
        val subclasses = mutableSetOf<KClass<*>>()
        subclasses.addAllSubclassForSealed(RuntimeEvent::class)

        val entries = RuntimeEvent.entries().map { it::class }

        assertEquals(subclasses.size, entries.size)

        for (subclass in subclasses) {
            assertTrue(entries.contains(subclass))
        }
    }

    private fun MutableSet<KClass<*>>.addAllSubclassForSealed(root: KClass<*>) {
        for (clazz in root.sealedSubclasses) {
            if (clazz.isSealed) {
                addAllSubclassForSealed(clazz)
            } else {
                add(clazz)
            }
        }
    }
}
