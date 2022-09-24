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
import kmp.tor.env
import org.jetbrains.kotlin.gradle.plugin.KotlinJsCompilerType

plugins {
    id(pluginId.kmp.configuration)
    id(pluginId.kmp.publish)
}

kmpConfiguration {
    setupMultiplatform(targets =
        setOf(

            KmpTarget.Jvm.Android(
                compileSdk = versions.android.sdkCompile,
                minSdk = versions.android.sdkMin16,
                buildTools = versions.android.buildTools,
                target = {
                    publishLibraryVariants("release")
                },
            ),

            KmpTarget.Jvm.Jvm.DEFAULT,

            KmpTarget.NonJvm.JS(
                compilerType = KotlinJsCompilerType.BOTH,
                browser = null,
                node = KmpTarget.NonJvm.JS.Node(),
            ),

            KmpTarget.NonJvm.Native.Unix.Linux.X64.DEFAULT,

            KmpTarget.NonJvm.Native.Mingw.X64.DEFAULT,
        ) +
        KmpTarget.NonJvm.Native.Unix.Darwin.Ios.ALL_DEFAULT     +
        KmpTarget.NonJvm.Native.Unix.Darwin.Macos.ALL_DEFAULT   +
        KmpTarget.NonJvm.Native.Unix.Darwin.Tvos.ALL_DEFAULT    +
        KmpTarget.NonJvm.Native.Unix.Darwin.Watchos.ALL_DEFAULT,

        commonPluginIdsPostConfiguration = setOf(pluginId.kotlin.atomicfu),

        commonMainSourceSet = {
            dependencies {
                api(project(":library:extensions:kmp-tor-ext-callback-controller-common"))
                implementation(project(":library:manager:kmp-tor-manager-common"))
            }
        },

        commonTestSourceSet = {
            dependencies {
                implementation(kotlin("test"))
            }
        },
    )
}

kmpPublish {
    setupModule(
        pomDescription = "Kotlin Components' Callback extension of TorManager common code for non-coroutine consumers",
        holdPublication = env.kmpTorAll.isBinaryRelease || env.kmpTorCommon.holdPublication
    )
}
