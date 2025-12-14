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
plugins {
    id("configuration")
}

kmpConfiguration {
    configureShared(java9ModuleName = "io.matthewnelson.kmp.tor.runtime.ctrl", publish = true) {
        common {
            sourceSetMain {
                dependencies {
                    api(project(":library:runtime-core"))
                    implementation(libs.encoding.core) // TODO: utf8
                    implementation(libs.immutable.collections)
                    implementation(libs.kmp.process)
                    implementation(libs.kmp.tor.common.core)
                    implementation(libs.kotlinx.coroutines.core)
                }
            }
            sourceSetTest {
                dependencies {
                    implementation(libs.encoding.base16)
                    implementation(libs.kmp.tor.resource.exec.tor)
                    implementation(libs.kmp.tor.resource.noexec.tor)
                    implementation(libs.kotlinx.coroutines.test)
                }
            }
        }

        kotlin {
            with(sourceSets) {
                val sets = arrayOf(
                    "jvm",
                    "native",
                ).mapNotNull { name ->
                    val main = findByName(name + "Main") ?: return@mapNotNull null
                    main to getByName(name + "Test")
                }
                if (sets.isEmpty()) return@kotlin
                val main = maybeCreate("nonJsMain").apply { dependsOn(getByName("commonMain")) }
                val test = maybeCreate("nonJsTest").apply { dependsOn(getByName("commonTest")) }
                sets.forEach { (m, t) -> m.dependsOn(main); t.dependsOn(test) }
            }
        }

        kotlin {
            with(sourceSets) {
                val sets = arrayOf(
                    "androidNative",
                    "linux",
                    "macos",
                    "mingw",
                ).mapNotNull { name -> findByName(name + "Test") }
                if (sets.isEmpty()) return@kotlin
                val test = maybeCreate("nonAppleMobileTest").apply { dependsOn(getByName("commonTest")) }
                sets.forEach { t -> t.dependsOn(test) }
            }
        }

        kotlin {
            sourceSets.findByName("jsWasmJsTest")?.dependencies {
                var v = libs.versions.kmp.tor.resource.get()
                if (v.endsWith("-SNAPSHOT")) {
                    v += libs.versions.kmp.tor.resourceNpmSNAPSHOT.get()
                }
                implementation(npm("kmp-tor.resource-exec-tor.all", v))
            }
        }
    }
}
