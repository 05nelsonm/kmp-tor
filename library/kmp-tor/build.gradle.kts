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
import io.matthewnelson.kotlin.components.dependencies.deps
import io.matthewnelson.kotlin.components.dependencies.depsTest
import io.matthewnelson.kotlin.components.dependencies.versions
import io.matthewnelson.kotlin.components.kmp.KmpTarget
import io.matthewnelson.kotlin.components.kmp.publish.kmpPublishRootProjectConfiguration
import io.matthewnelson.kotlin.components.kmp.util.*
import kmp.tor.env
import org.jetbrains.kotlin.gradle.plugin.KotlinJsCompilerType

plugins {
    id(pluginId.kmp.configuration)
    id(pluginId.kmp.publish)
}

val pConfig = kmpPublishRootProjectConfiguration!!
includeStagingRepoIfTrue(env.kmpTorBinaries.pollStagingRepo)

kmpConfiguration {
    setupMultiplatform(targets =
        setOf(

            KmpTarget.Jvm.Android(
                buildTools = versions.android.buildTools,
                compileSdk = versions.android.sdkCompile,
                minSdk = versions.android.sdkMin21,
                target = {
                    publishLibraryVariants("release")
                },
                mainSourceSet = {
                    dependencies {
                        implementation("${pConfig.group}:kmp-tor-binary-android:${env.kmpTorBinaries.version.name}")
                    }
                },
            ),

            KmpTarget.Jvm.Jvm(
                mainSourceSet = {
                    dependencies {
                        implementation(project(":library:kmp-tor-internal"))
                    }
                },
                testSourceSet = {
                    dependencies {
                        implementation(project(":library:extensions:kmp-tor-ext-unix-socket"))

                        // TODO: Remove once js binary targets are published
                        implementation("${pConfig.group}:kmp-tor-binary-linuxx64:${env.kmpTorBinaries.version.name}")
                        implementation("${pConfig.group}:kmp-tor-binary-linuxx86:${env.kmpTorBinaries.version.name}")
                        implementation("${pConfig.group}:kmp-tor-binary-macosx64:${env.kmpTorBinaries.version.name}")
                        implementation("${pConfig.group}:kmp-tor-binary-mingwx64:${env.kmpTorBinaries.version.name}")
                        implementation("${pConfig.group}:kmp-tor-binary-mingwx86:${env.kmpTorBinaries.version.name}")
                    }
                },
            ),

//            KmpTarget.NonJvm.JS(
//                compilerType = KotlinJsCompilerType.BOTH,
//                browser = null,
//                node = KmpTarget.NonJvm.JS.Node(),
//            ),
//
//            KmpTarget.NonJvm.Native.Unix.Darwin.Ios.Arm32.DEFAULT,
//            KmpTarget.NonJvm.Native.Unix.Darwin.Ios.Arm64.DEFAULT,
//            KmpTarget.NonJvm.Native.Unix.Darwin.Ios.X64.DEFAULT,
//            KmpTarget.NonJvm.Native.Unix.Darwin.Ios.SimulatorArm64.DEFAULT,
//
//            KmpTarget.NonJvm.Native.Unix.Darwin.Macos.X64(
//                mainSourceSet = {
//                    dependencies {
//                        // TODO: Uncomment once macosx64 binary target is published
//                        implementation("${pConfig.group}:kmp-tor-binary-macosx64:${env.kmpTorBinaries.version.name}")
//                    }
//                },
//            ),
//            KmpTarget.NonJvm.Native.Unix.Darwin.Macos.Arm64.DEFAULT,
//
//            KmpTarget.NonJvm.Native.Unix.Darwin.Tvos.Arm64.DEFAULT,
//            KmpTarget.NonJvm.Native.Unix.Darwin.Tvos.X64.DEFAULT,
//            KmpTarget.NonJvm.Native.Unix.Darwin.Tvos.SimulatorArm64.DEFAULT,
//
//            KmpTarget.NonJvm.Native.Unix.Darwin.Watchos.Arm32.DEFAULT,
//            KmpTarget.NonJvm.Native.Unix.Darwin.Watchos.Arm64.DEFAULT,
//            KmpTarget.NonJvm.Native.Unix.Darwin.Watchos.X64.DEFAULT,
//            KmpTarget.NonJvm.Native.Unix.Darwin.Watchos.X86.DEFAULT,
//            KmpTarget.NonJvm.Native.Unix.Darwin.Watchos.SimulatorArm64.DEFAULT,
//
//            KmpTarget.NonJvm.Native.Unix.Linux.X64(
//                mainSourceSet = {
//                    dependencies {
//                        // TODO: Uncomment once linuxx64 binary target is published
//                        implementation("${pConfig.group}:kmp-tor-binary-linuxx64:${env.kmpTorBinaries.version.name}")
//                    }
//                },
//            ),
//
//            KmpTarget.NonJvm.Native.Mingw.X64(
//                mainSourceSet = {
//                    dependencies {
//                        // TODO: Uncomment once mingwx64 binary target is published
//                        implementation("${pConfig.group}:kmp-tor-binary-mingwx64:${env.kmpTorBinaries.version.name}")
//                    }
//                },
//            ),
        ),

        commonMainSourceSet = {
            dependencies {
                implementation(deps.kotlin.coroutines.core.core)

                implementation("${pConfig.group}:kmp-tor-binary-geoip:${env.kmpTorBinaries.version.name}")
                implementation("${pConfig.group}:kmp-tor-binary-extract:${env.kmpTorBinaries.version.name}")

                api(project(":library:manager:kmp-tor-manager"))
            }
        },

        commonTestSourceSet = {
            dependencies {
                implementation(depsTest.kotlin.coroutines)
                implementation(kotlin("test"))
                implementation(project(":library:extensions:kmp-tor-ext-callback-manager"))
            }
        },

        kotlin = {
            sourceSetJvmJsTest {
                dependencies {
                    // TODO: Uncomment once js binary targets are published
//                    implementation("${pConfig.group}:kmp-tor-binary-linuxx64:${env.kmpTorBinaries.version.name}")
//                    implementation("${pConfig.group}:kmp-tor-binary-linuxx86:${env.kmpTorBinaries.version.name}")
//                    implementation("${pConfig.group}:kmp-tor-binary-macosx64:${env.kmpTorBinaries.version.name}")
//                    implementation("${pConfig.group}:kmp-tor-binary-mingwx64:${env.kmpTorBinaries.version.name}")
//                    implementation("${pConfig.group}:kmp-tor-binary-mingwx86:${env.kmpTorBinaries.version.name}")
                }
            }
        }
    )
}

kmpPublish {
    setupModule(
        pomDescription = "Kotlin Components' TorManager & TorBinary distribution",
        holdPublication = env.kmpTorAll.holdPublication,
        versionCodeOverride = env.kmpTorAll.version.code,
        versionNameOverride = env.kmpTorAll.version.name
    )
}
