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
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.Family

plugins {
    id("configuration")
}

kmpConfiguration {
    configureShared(java9ModuleName = "io.matthewnelson.kmp.tor.runtime.ctrl", publish = true) {
        js {
            sourceSetTest {
                dependencies {
                    implementation(npm("kmp-tor.resource-exec-tor.all", libs.versions.kmp.tor.resource.get()))
                }
            }
        }

        common {
            sourceSetMain {
                dependencies {
                    api(project(":library:runtime-core"))
                    implementation(libs.immutable.collections)
                    implementation(libs.kmp.process)
                    implementation(libs.kmp.tor.common.core)
                    implementation(libs.kotlinx.coroutines.core)
                }
            }
            sourceSetTest {
                dependencies {
                    implementation(libs.kmp.tor.resource.exec.tor)
                    implementation(libs.kmp.tor.resource.noexec.tor)
                    implementation(libs.kotlinx.coroutines.test)
                }
            }
        }

        kotlin {
            with(sourceSets) {
                val jvmMain = findByName("jvmMain")
                val nativeMain = findByName("nativeMain")

                if (jvmMain != null || nativeMain != null) {
                    val nonJsMain = maybeCreate("nonJsMain")
                    val nonJsTest = maybeCreate("nonJsTest")

                    nonJsMain.dependsOn(getByName("commonMain"))
                    nonJsTest.dependsOn(getByName("commonTest"))

                    jvmMain?.apply { dependsOn(nonJsMain) }
                    findByName("jvmTest")?.apply { dependsOn(nonJsTest) }
                    nativeMain?.apply { dependsOn(nonJsMain) }
                    findByName("nativeTest")?.apply { dependsOn(nonJsTest) }
                }
            }
        }

        kotlin {
            val cInteropDir = projectDir
                .resolve("src")
                .resolve("nativeInterop")
                .resolve("cinterop")

            targets.filterIsInstance<KotlinNativeTarget>().forEach target@ { target ->
                if (target.konanTarget.family != Family.IOS) return@target

                target
                    .compilations["main"]
                    .cinterops.create("un")
                    .defFile(cInteropDir.resolve("un.def"))
            }

            afterEvaluate {
                val commonizeTask = tasks.findByName("commonizeCInterop") ?: return@afterEvaluate

                tasks.all {
                    if (!name.endsWith("MetadataElements")) return@all
                    dependsOn(commonizeTask)
                }
            }
        }
    }
}
