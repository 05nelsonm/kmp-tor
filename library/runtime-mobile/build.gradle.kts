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
        androidLibrary(namespace = "io.matthewnelson.kmp.tor.runtime.mobile") {
            target { publishLibraryVariants("release") }

            android {
                lint {
                    // linter does not like the subclass. Runtime tests
                    // are performed to ensure everything is copacetic.
                    disable.add("EnsureInitializerMetadata")
                }

                defaultConfig {
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
            }

            sourceSetMain {
                dependencies {
                    implementation(libs.androidx.startup.runtime)
                    implementation(libs.kotlinx.coroutines.android)
                }
            }

            sourceSetTest {
                dependencies {
                    implementation(libs.kmp.tor.core.lib.locator)
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
            pluginIds("publication")

            sourceSetMain {
                dependencies {
                    api(project(":library:runtime"))
                }
            }
            sourceSetTest {
                dependencies {
                    implementation(kotlin("test"))
                }
            }
        }

        kotlin { explicitApi() }
    }
}
