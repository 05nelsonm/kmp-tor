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

import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.tor.runtime.core.TorConfig
import io.matthewnelson.kmp.tor.runtime.test.TestUtils
import io.matthewnelson.kmp.tor.runtime.test.TestUtils.testEnv
import kotlin.test.Test

class TorConfigKeywordsUnitTest {

    /**
     * This test runs in order to check all [TorConfig.Keyword] implemented in
     * [TorConfig] against those that are listed from tor CLI via the
     * `--list-torrc-options` and `--list-deprecated-options` commands. It
     * ensures:
     *
     *  - That all implemented keywords match what tor lists as available
     *  - That any keywords that were deprecated can be updated.
     *  - That all keywords are listed in [TestUtils.KEYWORDS]
     * */
    @Test
    fun givenKeywords_whenCheckedAgainstTorCLI_thenAreAsExpected() {
        val paths = testEnv("test_config_keywords")
            .torResource
            .install()

        val optionsCurrent = Process.Builder(paths.tor.toString())
            .args("--list-torrc-options")
            .output()
            .stdout
//            .also { println("CURRENT:\n$it") }
            .lines()
            .mapTo(ArrayList()) { "TorConfig.$it" }

        val optionsDeprecated = Process.Builder(paths.tor.toString())
            .args("--list-deprecated-options")
            .output()
            .stdout
//            .also { println("DEPRECATED:\n$it") }
            .lines()
            .map { "TorConfig.$it" }

        // Remove overlapping deprecated options
        optionsDeprecated.forEach { optionsCurrent.remove(it) }

        val testUtilsMissing = mutableSetOf<String>()
        val implementedNotFound = mutableSetOf<String>()
        val implementedDeprecated = mutableSetOf<String>()

        val testUtilKeywordClasses = TestUtils.KEYWORDS.map { it::class }

        (TorConfig.Setting.Factory::class.sealedSubclasses + TorConfig.Keyword::class.sealedSubclasses).forEach { clazz ->
            if (!clazz.isCompanion) return@forEach

            val name = clazz.qualifiedName!!
                .substringAfter("kmp.tor.runtime.core.")
                .substringBeforeLast('.')

            if (!testUtilKeywordClasses.contains(clazz)) {
                testUtilsMissing.add(name)
            }

            if (optionsCurrent.remove(name)) {
                return@forEach
            }

            if (optionsDeprecated.contains(name)) {
                implementedDeprecated.add(name)
                return@forEach
            }

            implementedNotFound.add(name)
        }

        val sb = StringBuilder()

        run {
            val title = "The following settings were listed by --list-torrc-options, but not implemented in TorConfig"

            // Override
            listOf(
                "TorConfig.FirewallPorts",              // deprecated
                "TorConfig.ReconfigDropsBridgeDescs",
            ).forEach { optionsCurrent.remove(it) }

            if (optionsCurrent.isNotEmpty()) {
                sb.append(title)

                for (item in optionsCurrent) {
                    sb.appendLine()
                    sb.append(item)
                }
            }
        }

        run {
            val title = "The following settings were not listed by --list-torrc-options, but are implemented in TorConfig"

            // Override
            listOf(
                "TorConfig.AndroidIdentityTag",
            ).forEach { implementedNotFound.remove(it) }

            if (implementedNotFound.isNotEmpty()) {
                if (sb.isNotEmpty()) {
                    sb.appendLine()
                    sb.appendLine()
                }

                sb.append(title)

                for (item in implementedNotFound) {
                    sb.appendLine()
                    sb.append(item)
                }
            }
        }

        run {
            val title = "The following settings are implemented in TorConfig and are now deprecated by --list-deprecated-options"

            if (implementedDeprecated.isNotEmpty()) {
                if (sb.isNotEmpty()) {
                    sb.appendLine()
                    sb.appendLine()
                }

                sb.append(title)

                for (item in implementedDeprecated) {
                    sb.appendLine()
                    sb.append(item)
                }
            }
        }

        run {
            val title = "The following settings are implemented in TorConfig, but were not listed in TestUtils.KEYWORDS"

            if (testUtilsMissing.isNotEmpty()) {
                if (sb.isNotEmpty()) {
                    sb.appendLine()
                    sb.appendLine()
                }

                sb.append(title)

                for (item in testUtilsMissing) {
                    sb.appendLine()
                    sb.append(item)
                }
            }
        }

        // No errors
        if (sb.isEmpty()) return

        throw AssertionError(sb.toString())
    }

    @Test
    fun givenKeywords_whenTestKeywords_thenClassNameMatchesName() {
        val doesNotMatch = mutableSetOf<TorConfig.Keyword>()
        for (kw in TestUtils.KEYWORDS) {
            if (kw::class.qualifiedName!!.contains("TorConfig.${kw.name}")) continue
            doesNotMatch.add(kw)
        }

        if (doesNotMatch.isEmpty()) return

        val msg = doesNotMatch.joinToString(
            separator = "\n",
            prefix = "The following TorConfig.Keyword.name do not match the className\n"
        )

        throw AssertionError(msg)
    }
}
