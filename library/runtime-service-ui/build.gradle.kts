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
    configure {
        androidLibrary(namespace = "io.matthewnelson.kmp.tor.runtime.service.ui") {
            target { publishLibraryVariants("release") }

            android {
                defaultConfig {
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }

                sourceSets["androidTest"].manifest.srcFile(
                    projectDir
                        .resolve("src")
                        .resolve("androidInstrumentedTest")
                        .resolve("AndroidManifest.xml")
                )
            }

            sourceSetTest {
                dependencies {
                    implementation(libs.kmp.tor.resource.android.unit.test)
                }
            }

            sourceSetTestInstrumented {
                dependencies {
                    implementation(libs.androidx.test.core)
                    implementation(libs.androidx.test.runner)
                }
            }
        }

        iosAll()

        common {
            pluginIds("publication", "dokka")

            sourceSetMain {
                dependencies {
                    api(project(":library:runtime-service"))
                    implementation(libs.immutable.collections)
                    implementation(libs.kotlinx.coroutines.core)
                }
            }

            sourceSetTest {
                dependencies {
                    implementation(kotlin("test"))
                    implementation(libs.kmp.tor.resource.tor)
                    implementation(libs.kotlinx.coroutines.test)
                }
            }
        }

        kotlin { explicitApi() }
    }
}
