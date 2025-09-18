/*
 * Copyright (c) 2025 Matthew Nelson
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
import io.matthewnelson.kmp.configuration.extension.container.target.KmpConfigurationContainerDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

fun KmpConfigurationContainerDsl.configureKotlinVersionCompatibility() {
    kotlin {
        if (!project.plugins.hasPlugin("publication")) return@kotlin

        @Suppress("DEPRECATION")
        compilerOptions {
            freeCompilerArgs.add("-Xsuppress-version-warnings")
            // kmp-file, kmp-tor-common, & kmp-process use 1.9
            apiVersion.set(KotlinVersion.KOTLIN_1_9)
            languageVersion.set(KotlinVersion.KOTLIN_1_9)
        }
        sourceSets.configureEach {
            if (name.startsWith("js") || name.startsWith("wasm")) {
                languageSettings {
                    // kmp-file, kmp-tor-common, & kmp-process use 2.0 for js/wasmJs/jsWasmJs source sets
                    apiVersion = KotlinVersion.KOTLIN_2_0.version
                    languageVersion = KotlinVersion.KOTLIN_2_0.version
                }
            }
        }
    }
}
