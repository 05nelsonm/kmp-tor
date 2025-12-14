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
import io.matthewnelson.kmp.tor.common.api.ResourceLoader
import io.matthewnelson.kmp.tor.runtime.core.config.TorOption
import io.matthewnelson.kmp.tor.runtime.internal.TorDaemon
import io.matthewnelson.kmp.tor.runtime.test.runTorTest
import kotlin.test.Test
import kotlin.test.assertIs

/**
 * Simply verifies that all [TorOption.entries] are accounted for by
 * the Tor C library's CLI command `--list-torrc-options`. This is to
 * "catch" any additions, deprecations, etc.
 * */
class TorOptionEntriesIntegrationTest {

    @Test
    fun givenAllTorOption_whenCheckedAgainstTorCLIListed_thenAreAsExpected() = runTorTest { runtime ->
        val loader = runtime.environment().loader
        assertIs<ResourceLoader.Tor.Exec>(loader)

        val (cliActive, cliDeprecated) = listOf(
            "--list-torrc-options",
            "--list-deprecated-options",
        ).map { cmd ->
            val out = loader.process(TorDaemon.torBinder) { tor, configureEnv ->
                Process.Builder(tor.path)
                    .args(cmd)
                    .environment(configureEnv)
            }.createOutput { timeoutMillis = 3_000 }

            if (
                out.stdout.isEmpty()
                || out.stderr.isNotEmpty()
                || out.processError != null
            ) {
                val msg = buildString {
                    appendLine(out.stdout)
                    appendLine(out.stderr)
                    appendLine(out.toString())
                }
                throw AssertionError(msg)
            }

//            println("CMD[$cmd]:\n${out.stdout}")

            out.stdout.lines().toMutableList()
        }.let { it.first() to it.last() }

        // Remove overlapping deprecated options
        cliDeprecated.forEach { cliActive.remove(it) }

        val kmpTorNotFound = mutableListOf<String>()
        val kmpTorDeprecated = mutableListOf<String>()

        TorOption.entries.forEach { entry ->
            if (cliActive.remove(entry.name)) {
                // kmp-tor implements active option as TorOption
                return@forEach
            }

            if (cliDeprecated.contains(entry.name)) {
                // kmp-tor implements deprecated option as TorOption
                kmpTorDeprecated.add(entry.name)
                return@forEach
            }

            // kmp-tor implements entry not found in either
            // active or deprecated lists.
            kmpTorNotFound.add(entry.name)
        }

        val sb = StringBuilder()

        run {
            val title = "The following settings were listed by " +
                        "--list-torrc-options, but not implemented " +
                        "by kmp-tor as TorOption."

            // Override (kmp-tor does not implement these as TorOption)
            listOf(
                "FirewallPorts",                    // deprecated
                "ReconfigDropsBridgeDescs",
            ).forEach { cliActive.remove(it) }

            if (cliActive.isEmpty()) return@run

            // CLI contains active options not currently
            // implemented by kmp-tor (tor added an option?)
            sb.append(title)
            for (option in cliActive) {
                sb.appendLine()
                sb.append(option)
            }
        }

        run {
            val title = "The following settings were NOT listed by " +
                        "--list-torrc-options, but are implemented " +
                        "by kmp-tor as TorOption."

            // Override (kmp-tor implements these obsolete TorOption)
            listOf<String>(
                // Currently nothing...
            ).forEach { kmpTorNotFound.remove(it) }

            if (kmpTorNotFound.isEmpty()) return@run

            if (sb.isNotEmpty()) {
                sb.appendLine().appendLine()
            }

            // CLI does NOT contain the TorOption which
            // kmp-tor implements. (tor obsoleted an option?)
            sb.append(title)
            for (option in kmpTorNotFound) {
                sb.appendLine()
                sb.append(option)
            }
        }

        run {
            val title = "The following settings are implemented " +
                        "by kmp-tor as TorOption, and are listed as " +
                        "deprecated by --list-deprecated-options"

            if (kmpTorDeprecated.isEmpty()) return@run

            if (sb.isNotEmpty()) {
                sb.appendLine().appendLine()
            }

            // CLI contains a new deprecated item for which
            // our "override" did not account for. (Need to
            // deprecate something in kmp-tor?)
            sb.append(title)
            for (option in kmpTorDeprecated) {
                sb.appendLine()
                sb.append(option)
            }
        }

        // No errors. kmp-tor does not need to update TorOption
        if (sb.isEmpty()) return@runTorTest

        throw AssertionError(sb.toString())
    }
}
