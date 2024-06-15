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
import io.matthewnelson.kmp.configuration.ExperimentalKmpConfigurationApi

plugins {
    id("configuration")
}

kmpConfiguration {
    configureShared(publish = true) {
        jvm {
            @OptIn(ExperimentalKmpConfigurationApi::class)
            java9ModuleInfoName = "io.matthewnelson.kmp.tor.runtime"

            sourceSetTest {
                dependencies {
                    implementation(kotlin("reflect"))
                }
            }
        }

        common {
            sourceSetMain {
                dependencies {
                    api(project(":library:runtime-core"))
                    implementation(project(":library:runtime-ctrl"))
                    implementation(libs.encoding.base16)
                    implementation(libs.kmp.process)
                    implementation(libs.kmp.tor.core.resource)
                    implementation(kotlincrypto.secureRandom)
                    implementation(kotlincrypto.hash.sha2)
                    implementation(libs.kotlinx.coroutines.core)
                }
            }

            sourceSetTest {
                dependencies {
                    implementation(libs.kmp.tor.resource.tor)
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
    }
}
