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
import io.matthewnelson.kotlin.components.dependencies.versions
import io.matthewnelson.kotlin.components.kmp.KmpTarget
import io.matthewnelson.kotlin.components.kmp.publish.kmpPublishRootProjectConfiguration
import kmp.tor.env
import org.jetbrains.kotlin.gradle.plugin.KotlinJsCompilerType

plugins {
    id("kmp-configuration")
}

val pConfig = kmpPublishRootProjectConfiguration!!

repositories {
    if (pConfig.versionName.endsWith("-SNAPSHOT")) {
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
    } else {
        maven("https://oss.sonatype.org/content/groups/staging") {
            credentials {
                username = rootProject.ext.get("mavenCentralUsername").toString()
                password = rootProject.ext.get("mavenCentralPassword").toString()
            }
        }
    }
}

kmpConfiguration {
    setupMultiplatform(
        setOf(

            KmpTarget.Jvm.Jvm(
                mainSourceSet = {
                    dependencies {
                        implementation("${pConfig.group}:kmp-tor:${env.kmpTorAll.version.name}")
                        implementation("${pConfig.group}:kmp-tor-controller:${env.kmpTor.version.name}")
                        implementation("${pConfig.group}:kmp-tor-ext-callback-controller:${env.kmpTor.version.name}")
                        implementation("${pConfig.group}:kmp-tor-ext-callback-manager:${env.kmpTor.version.name}")
                        implementation("${pConfig.group}:kmp-tor-manager:${env.kmpTor.version.name}")
                    }
                }
            ),

            KmpTarget.Jvm.Android(
                buildTools = versions.android.buildTools,
                compileSdk = versions.android.sdkCompile,
                minSdk = versions.android.sdkMin16,
                mainSourceSet = {
                    dependencies {
                        implementation("${pConfig.group}:kmp-tor:${env.kmpTorAll.version.name}")
                        implementation("${pConfig.group}:kmp-tor-controller:${env.kmpTor.version.name}")
                        implementation("${pConfig.group}:kmp-tor-ext-callback-controller:${env.kmpTor.version.name}")
                        implementation("${pConfig.group}:kmp-tor-ext-callback-manager:${env.kmpTor.version.name}")
                        implementation("${pConfig.group}:kmp-tor-manager:${env.kmpTor.version.name}")
                    }
                }
            ),

            KmpTarget.NonJvm.JS(
                compilerType = KotlinJsCompilerType.BOTH,
                browser = null,
                node = KmpTarget.NonJvm.JS.Node(
                    jsNodeDsl = null
                )
            ),

            // TODO: Uncomment once ios target is published
            KmpTarget.NonJvm.Native.Unix.Darwin.Ios.All.DEFAULT,

            // TODO: Uncomment once macosx64 target is published
            KmpTarget.NonJvm.Native.Unix.Darwin.Macos.X64.DEFAULT,

            // TODO: Uncomment once macosarm64 target is published
            KmpTarget.NonJvm.Native.Unix.Darwin.Macos.Arm64.DEFAULT,

            // TODO: Uncomment once tvos target is published
            KmpTarget.NonJvm.Native.Unix.Darwin.Tvos.All.DEFAULT,

            // TODO: Uncomment once watchos target is published
            KmpTarget.NonJvm.Native.Unix.Darwin.Watchos.All.DEFAULT,

            // TODO: Uncomment once linuxx64 target is published
            KmpTarget.NonJvm.Native.Unix.Linux.X64.DEFAULT,

            // TODO: Uncomment once mingwx64 target is published
            KmpTarget.NonJvm.Native.Mingw.X64.DEFAULT,
        ),
        commonMainSourceSet = {
            dependencies {
                implementation("${pConfig.group}:kmp-tor-common:${env.kmpTor.version.name}")
                implementation("${pConfig.group}:kmp-tor-controller-common:${env.kmpTor.version.name}")
                implementation("${pConfig.group}:kmp-tor-ext-callback-common:${env.kmpTor.version.name}")
                implementation("${pConfig.group}:kmp-tor-ext-callback-controller-common:${env.kmpTor.version.name}")
                implementation("${pConfig.group}:kmp-tor-ext-callback-manager-common:${env.kmpTor.version.name}")
                implementation("${pConfig.group}:kmp-tor-manager-common:${env.kmpTor.version.name}")
            }
        }
    )
}
