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

import io.matthewnelson.kmp.tor.runtime.core.TorConfig
import io.matthewnelson.kmp.tor.runtime.test.TestUtils
import kotlin.reflect.KClass
import kotlin.test.Test

class TorConfigKeywordsUnitTest {

    @Test
    fun givenKeywords_whenTestKeywords_thenContainsAll() {
        val missing = mutableSetOf<KClass<out TorConfig.Keyword>>()
        val testKeywords = TestUtils.KEYWORDS.map { it::class }

        for (clazz in TorConfig.Keyword::class.sealedSubclasses) {
            if (!clazz.isCompanion) continue
            if (!testKeywords.contains(clazz)) {
                missing.add(clazz)
            }
        }

        if (missing.isEmpty()) return

        val msg = missing.joinToString(
            "\n",
            prefix = "The following TorConfig.Keyword classes are missing from TorCmdUnitTest.KEYWORDS\n",
            transform = {
                it.qualifiedName!!
                    .substringAfter("kmp.tor.runtime.core.")
                    .substringBeforeLast('.')
            }
        )

        throw AssertionError(msg)
    }

    @Test
    fun givenKeywords_whenTestKeywords_thenClassNameMatchesName() {
        val unmatched = mutableSetOf<TorConfig.Keyword>()
        for (kw in TestUtils.KEYWORDS) {
            if (kw::class.qualifiedName!!.contains("TorConfig.${kw.name}")) continue
            unmatched.add(kw)
        }

        if (unmatched.isEmpty()) return

        val msg = unmatched.joinToString(
            separator = "\n",
            prefix = "The following TorConfig.Keyword.name do not match the className\n"
        )

        throw AssertionError(msg)
    }
}
