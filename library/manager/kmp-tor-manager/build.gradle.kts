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
import io.matthewnelson.kotlin.components.kmp.KmpTarget.Jvm.Android.Companion.SOURCE_SET_MAIN_NAME as KmpAndroidMain
import io.matthewnelson.kotlin.components.kmp.publish.kmpPublishRootProjectConfiguration
import kmp.tor.env
import org.jetbrains.kotlin.gradle.plugin.KotlinJsCompilerType

plugins {
    id("kmp-configuration")
    id("kmp-publish")
}

kmpConfiguration {
    setupMultiplatform(
        setOf(

            KmpTarget.Jvm.Jvm.DEFAULT,

            KmpTarget.Jvm.Android(
                buildTools = versions.android.buildTools,
                compileSdk = versions.android.sdkCompile,
                minSdk = versions.android.sdkMin16,
                target = {
                    publishLibraryVariants("release")
                },
                androidMainSourceSet = {
                    manifest.srcFile("$projectDir/src/$KmpAndroidMain/AndroidManifest.xml")
                    res.setSrcDirs(listOf("$projectDir/src/$KmpAndroidMain/res"))
                },
            ),

            KmpTarget.NonJvm.JS(
                compilerType = KotlinJsCompilerType.BOTH,
                browser = null,
                node = KmpTarget.NonJvm.JS.Node(
                    jsNodeDsl = null
                ),
                mainSourceSet = null,
            ),

            KmpTarget.NonJvm.Native.Unix.Darwin.Ios.All.DEFAULT,
            KmpTarget.NonJvm.Native.Unix.Darwin.Macos.X64.DEFAULT,
            KmpTarget.NonJvm.Native.Unix.Darwin.Macos.Arm64.DEFAULT,
            KmpTarget.NonJvm.Native.Unix.Darwin.Tvos.All.DEFAULT,
            KmpTarget.NonJvm.Native.Unix.Darwin.Watchos.All.DEFAULT,

            KmpTarget.NonJvm.Native.Unix.Linux.X64.DEFAULT,

            KmpTarget.NonJvm.Native.Mingw.X64.DEFAULT,
        ),
        commonPluginIds = setOf("kotlinx-atomicfu"),
        commonMainSourceSet = {
            dependencies {
                implementation(deps.kotlin.atomicfu.atomicfu)
                implementation(deps.kotlin.coroutines.core.core)
                implementation(deps.kotlin.reflect)
                implementation(project(":library:controller:kmp-tor-controller")) {
                    exclude(kmpPublishRootProjectConfiguration!!.group, "kmp-tor-common")
                    exclude(kmpPublishRootProjectConfiguration!!.group, "kmp-tor-controller-common")
                }
                api(project(":library:manager:kmp-tor-manager-common"))
            }
        },
        commonTestSourceSet = {
            dependencies {
                implementation(depsTest.kotlin.coroutines)
                implementation(kotlin("test"))
            }
        },
    )
}

kmpPublish {
    setupModule(
        pomDescription = "Kotlin Components' TorManager for managing startup, shutdown, state, etc.",
        holdPublication = env.kmpTor.holdPublication
    )
}
