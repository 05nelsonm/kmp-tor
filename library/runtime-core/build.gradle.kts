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
    configureShared(java9ModuleName = "io.matthewnelson.kmp.tor.runtime.core", publish = true) {
        jvm {
            sourceSetTest {
                dependencies {
                    implementation(kotlin("reflect"))
                    implementation(libs.bouncy.castle)
                    implementation(libs.jetbrains.skiko)
                }
            }
        }

        common {
            sourceSetMain {
                dependencies {
                    implementation(libs.encoding.base16)
                    implementation(libs.encoding.base32)
                    implementation(libs.encoding.base64)
                    implementation(libs.immutable.collections)
                    implementation(libs.kmp.process)
                    api(libs.kmp.tor.common.api)
                    implementation(libs.kmp.tor.common.core)
                    implementation(libs.kotlinx.coroutines.core)
                    implementation(kotlincrypto.bitops.endian)
                    api(kotlincrypto.error.error)
                    implementation(kotlincrypto.hash.sha2)
                    implementation(kotlincrypto.hash.sha3)
                    implementation(kotlincrypto.random.crypto.rand)
                }
            }
            sourceSetTest {
                dependencies {
                    implementation(libs.kotlinx.coroutines.test)
                }
            }
        }

        kotlin {
            with(sourceSets) {
                val jsMain = findByName("jsMain")
                val jvmMain = findByName("jvmMain")

                if (jsMain != null || jvmMain != null) {
                    val nonNativeMain = maybeCreate("nonNativeMain")
                    nonNativeMain.dependsOn(getByName("commonMain"))
                    jvmMain?.apply { dependsOn(nonNativeMain) }
                    jsMain?.apply { dependsOn(nonNativeMain) }
                }

                val nativeMain = findByName("nativeMain")
                val nativeTest = findByName("nativeTest")?.apply {
                    dependencies {
                        implementation(libs.ktor.network)
                    }
                }

                if (jvmMain != null || nativeMain != null) {
                    val nonJsMain = maybeCreate("nonJsMain")
                    val nonJsTest = maybeCreate("nonJsTest")

                    nonJsMain.dependsOn(getByName("commonMain"))
                    nonJsTest.dependsOn(getByName("commonTest"))

                    jvmMain?.apply { dependsOn(nonJsMain) }
                    findByName("jvmTest")?.apply { dependsOn(nonJsTest) }
                    nativeMain?.apply { dependsOn(nonJsMain) }
                    nativeTest?.apply { dependsOn(nonJsTest) }
                }
            }
        }

        kotlin {
            val cInteropDir = projectDir
                .resolve("src")
                .resolve("nativeInterop")
                .resolve("cinterop")

            targets.filterIsInstance<KotlinNativeTarget>().forEach target@ { target ->
                when (target.konanTarget.family) {
                    Family.ANDROID, Family.IOS, Family.TVOS, Family.WATCHOS -> {}
                    else -> return@target
                }

                target.compilations["main"].cinterops.create("network") {
                    definitionFile.set(cInteropDir.resolve("$name.def"))
                }
            }

            project.afterEvaluate {
                val commonizeTask = project.tasks.findByName("commonizeCInterop") ?: return@afterEvaluate

                project.tasks.all {
                    if (!name.endsWith("MetadataElements")) return@all
                    dependsOn(commonizeTask)
                }
            }
        }
    }
}
