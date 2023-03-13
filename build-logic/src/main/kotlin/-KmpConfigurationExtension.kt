/*
 * Copyright (c) 2023 Matthew Nelson
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
import io.matthewnelson.kmp.configuration.extension.KmpConfigurationExtension
import io.matthewnelson.kmp.configuration.extension.container.target.KmpConfigurationContainerDsl
import org.gradle.api.Action
import org.gradle.api.JavaVersion

fun KmpConfigurationExtension.configureShared(
    androidNameSpace: String? = null,
    isCommonModule: Boolean = false,
    publish: Boolean = false,
    explicitApi: Boolean = false,
    action: Action<KmpConfigurationContainerDsl>
) {
    configure {
        jvm {
            if (androidNameSpace == null) { target { withJava() } }

            kotlinJvmTarget = JavaVersion.VERSION_1_8
            compileSourceCompatibility = JavaVersion.VERSION_1_8
            compileTargetCompatibility = JavaVersion.VERSION_1_8
        }

        if (androidNameSpace != null) {
            androidLibrary {
                target { publishLibraryVariants("release") }

                android {
                    buildToolsVersion = "33.0.1"
                    compileSdk = 33
                    namespace = androidNameSpace

                    defaultConfig {
                        minSdk = 16
                        targetSdk = 33

                        testInstrumentationRunnerArguments["disableAnalytics"] = "true"
                    }
                }

                kotlinJvmTarget = JavaVersion.VERSION_1_8
                compileSourceCompatibility = JavaVersion.VERSION_1_8
                compileTargetCompatibility = JavaVersion.VERSION_1_8
            }
        }

        if (isCommonModule) {
            js {
                target {
                    nodejs {
                        testTask {
                            useMocha { timeout = "30s" }
                        }
                    }
                }
            }

            linuxX64()
            mingwX64()

            iosArm32()
            iosArm64()
            iosX64()
            iosSimulatorArm64()

            macosArm64()
            macosX64()

            tvosArm64()
            tvosX64()
            tvosSimulatorArm64()

            watchosArm32()
            watchosArm64()
            watchosX64()
            watchosX86()
            watchosSimulatorArm64()
        }

        common {
            if (publish) { pluginIds("publication") }

            sourceSetTest {
                dependencies {
                    implementation(kotlin("test"))
                }
            }
        }

        kotlin {
            if (explicitApi) { explicitApi() }

            with(sourceSets) {
                val jvmMain = findByName("jvmMain")
                val jsMain = findByName("jsMain")

                if (jvmMain != null || jsMain != null) {
                    val jvmJsMain = maybeCreate("jvmJsMain").apply {
                        dependsOn(getByName("commonMain"))
                    }
                    val jvmJsTest = maybeCreate("jvmJsTest").apply {
                        dependsOn(getByName("commonTest"))
                    }

                    jvmMain?.apply { dependsOn(jvmJsMain) }
                    findByName("jvmTest")?.apply { dependsOn(jvmJsTest) }

                    jsMain?.apply { dependsOn(jvmJsMain) }
                    findByName("jsTest")?.apply { dependsOn(jvmJsTest) }
                }
            }
        }

        action.execute(this)
    }
}
