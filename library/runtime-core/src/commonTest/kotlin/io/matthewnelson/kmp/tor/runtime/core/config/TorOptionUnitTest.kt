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

import io.matthewnelson.kmp.tor.runtime.core.config.TorOption.Companion.buildableInternal
import io.matthewnelson.kmp.tor.runtime.core.config.builder.BuilderScopeAutoBoolean
import io.matthewnelson.kmp.tor.runtime.core.config.builder.BuilderScopePort
import kotlin.test.*

class TorOptionUnitTest {

    @Test
    fun givenOptions_whenName_thenEqualsClassSimpleName() {
        // confirm it is a data object and not just an object
        TorOption.entries.forEach { option ->
            val sName = option::class.simpleName
            assertNotNull(sName)
            assertEquals(sName, option.name)
        }
    }

    @Test
    fun givenOptions_whenAttributeFILE_thenDoesNotContainAttributeDIRECTORYorPORTorUNIXSOCKET() {
        val options = TorOption.entries.mapNotNull { option ->
            if (!option.attributes.contains(TorOption.Attribute.FILE)) return@mapNotNull null
            option
        }

        // Force update expected test value when something
        // is added (same as reflection tests for Jvm).
        assertEquals(11, options.size)

        options.forEach { option ->
            val attrs = option.attributes
            assertFalse(attrs.contains(TorOption.Attribute.DIRECTORY))
            assertFalse(attrs.contains(TorOption.Attribute.PORT))
            assertFalse(attrs.contains(TorOption.Attribute.UNIX_SOCKET))
        }
    }

    @Test
    fun givenOptions_whenAttributeDIRECTORY_thenDoesNotContainAttributeFILEorPORTorUNIXSOCKET() {
        val options = TorOption.entries.mapNotNull { option ->
            if (!option.attributes.contains(TorOption.Attribute.DIRECTORY)) return@mapNotNull null
            option
        }

        // Force update expected test value when something
        // is added (same as reflection tests for Jvm).
        assertEquals(6, options.size)

        options.forEach { option ->
            val attrs = option.attributes
            assertFalse(attrs.contains(TorOption.Attribute.FILE))
            assertFalse(attrs.contains(TorOption.Attribute.PORT))
            assertFalse(attrs.contains(TorOption.Attribute.UNIX_SOCKET))
        }
    }

    @Test
    fun givenOptions_whenAttributePORT_thenDoesNotContainAttributeFILEorDIRECTORY() {
        val options = TorOption.entries.mapNotNull { option ->
            if (!option.attributes.contains(TorOption.Attribute.PORT)) return@mapNotNull null
            option
        }

        // Force update expected test value when something
        // is added (same as reflection tests for Jvm).
        assertEquals(21, options.size)

        options.forEach { option ->
            val attrs = option.attributes
            assertFalse(attrs.contains(TorOption.Attribute.FILE))
            assertFalse(attrs.contains(TorOption.Attribute.DIRECTORY))
        }
    }

    @Test
    fun givenOptions_whenAttributeUNIXSOCKET_thenDoesNotContainAttributeFILEorDIRECTORY() {
        val options = TorOption.entries.mapNotNull { option ->
            if (!option.attributes.contains(TorOption.Attribute.UNIX_SOCKET)) return@mapNotNull null
            option
        }

        // Force update expected test value when something
        // is added (same as reflection tests for Jvm).
        assertEquals(6, options.size)

        options.forEach { option ->
            val attrs = option.attributes
            assertFalse(attrs.contains(TorOption.Attribute.FILE))
            assertFalse(attrs.contains(TorOption.Attribute.DIRECTORY))
        }
    }

    @Test
    fun givenOptions_whenNonPersistentPort_thenIsNotUnique() {
        val options = TorOption.entries.mapNotNull { option ->
            if (option.name[0] != '_') return@mapNotNull null
            if (!option.attributes.contains(TorOption.Attribute.PORT)) return@mapNotNull null
            option
        }

        // Force update expected test value when something
        // is added (same as reflection tests for Jvm).
        assertEquals(10, options.size)

        options.forEach { option ->
            assertFalse(option.isUnique, "$option is non-persistent PORT, but isUnique was true")
        }
    }

    @Test
    fun givenOptions_whenInstanceConfigurableBuildable_thenImplementsBuildableFunction() {
        val options = TorOption.entries.filterIsInstance<ConfigurableBuildable<*>>()

        // Force update expected test value when something
        // is added (same as reflection tests for Jvm).
        assertEquals(29, options.size)

        options.forEach { option ->

            // Would throw exception if buildable was not implemented
            // because of a default return of null.
            //
            // Would also throw on lambda closure if
            // TorSetting.BuilderScope.build throws exception with the
            // TorOption.default arguments (it should not).
            option.buildContract {}
        }
    }

    @Test
    fun givenOptions_whenNotInstanceConfigurableBuildable_thenDoesNotImplementBuildableFunction() {
        val options = TorOption.entries.filter { it !is ConfigurableBuildable<*> }

        // Force update expected test value when something
        // is added (same as reflection tests for Jvm).
        assertEquals(328, options.size)

        options.forEach { option ->
            assertNull(option.buildableInternal())
        }
    }

    @Test
    fun givenOptions_whenBuildableAutoBoolean_thenDefaultIsAutoOrOneOrZero() {
        val options = TorOption.entries.mapNotNull { option ->
            val buildable = option.buildableInternal()
            if (buildable is BuilderScopeAutoBoolean) {
                option
            } else {
                null
            }
        }

        // Force update expected test value when something
        // is added (same as reflection tests for Jvm).
        assertEquals(18, options.size)

        for (option in options) {
            when (option.default) {
                TorOption.AUTO, "0", "1" -> { /* pass */ }
                else -> fail("TorOption.$option default[${option.default}] must be ${TorOption.AUTO}, 1, or 0")
            }
        }
    }

    @Test
    fun givenOptions_whenInstanceConfigurableBoolean_thenDefaultIsTrueOrFalse() {
        val options = TorOption.entries.filterIsInstance<ConfigurableBoolean>()

        // Force update expected test value when something
        // is added (same as reflection tests for Jvm).
        assertEquals(94, options.size)

        options.forEach { option ->
            when ((option as TorOption).default) {
                "1", "0" -> { /* pass */ }
                else -> fail("TorOption.$option default[${option.default}] must be 1 or 0")
            }
        }
    }

    @Test
    fun givenOptions_whenBuildablePort_thenHasAttributePORT() {
        val options = TorOption.entries.filter { option ->
            option.buildableInternal() is BuilderScopePort
        }

        // Force update expected test value when something
        // is added (same as reflection tests for Jvm).
        assertEquals(10, options.size)

        options.forEach { option ->
            val attrs = option.attributes
            val expected = TorOption.Attribute.PORT
            assertTrue(attrs.contains(expected), "$option does not contain Attribute.$expected")
        }
    }

    @Test
    fun givenOptions_whenInstanceConfigurableDirectory_thenHasAttributeDIRECTORY() {
        val options = TorOption.entries.filterIsInstance<ConfigurableDirectory>()

        // Force update expected test value when something
        // is added (same as reflection tests for Jvm).
        assertEquals(5, options.size)

        options.forEach { option ->
            val attrs = (option as TorOption).attributes
            val expected = TorOption.Attribute.DIRECTORY

            assertTrue(attrs.contains(expected), "$option does not contain Attribute.$expected")
        }
    }

    @Test
    fun givenOptions_whenInstanceConfigurableFile_thenHasAttributeFILE() {
        val options = TorOption.entries.filterIsInstance<ConfigurableFile>()

        // Force update expected test value when something
        // is added (same as reflection tests for Jvm).
        assertEquals(10, options.size)

        options.forEach { option ->
            val attrs = (option as TorOption).attributes
            val expected = TorOption.Attribute.FILE

            assertTrue(attrs.contains(expected), "$option does not contain Attribute.$expected")
        }
    }

    @Test
    fun givenOptions_whenHasAttributeHIDDENSERVICE_thenIsNotUnique() {
        val attr = TorOption.Attribute.HIDDEN_SERVICE
        val options = TorOption.entries.filter { it.attributes.contains(attr) }

        // Force update expected test value when something
        // is added (same as reflection tests for Jvm).
        assertEquals(16, options.size)

        options.forEach { option ->
            assertFalse(option.isUnique, "$option has Attribute.$attr, but isUnique was true")
        }
    }
}
