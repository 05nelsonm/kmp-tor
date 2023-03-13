/*
 * Copyright (c) 2022 Matthew Nelson
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
import kmp.tor.env

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    application
    id(libs.plugins.kotlin.jvm.get().pluginId)
    alias(libs.plugins.javafx)
}

// TODO
//// disregard. this is for playing with newly published binaries prior to release
//includeStagingRepoIfTrue(env.kmpTorBinaries.pollStagingRepo)
//
//// For SNAPSHOTS
//includeSnapshotsRepoIfTrue(true)

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

application {
    mainClass.set("io.matthewnelson.kmp.tor.sample.java.javafx.App")
}

javafx {
    modules("javafx.controls", "javafx.graphics")
}

dependencies {
    // For SNAPSHOTS disregard
//    implementation("io.matthewnelson.kotlin-components:kmp-tor:${env.kmpTorAll.version.name}")
//    implementation("io.matthewnelson.kotlin-components:kmp-tor-ext-callback-manager:${env.kmpTor.version.name}")

    val vTor = libs.versions.kmpTorBinary.get()
    val vKmpTor = libs.versions.kmpTor.get()

    // kmp-tor dependency
    implementation("io.matthewnelson.kotlin-components:kmp-tor:$vTor-$vKmpTor")
    // kmp-tor callback extension
    implementation("io.matthewnelson.kotlin-components:kmp-tor-ext-callback-manager:$vKmpTor")

    // Even through kmp-tor comes with coroutines under the hood
    // and expressing the dependency is _not_ needed in kotlin
    // projects, it _is_ needed in Java only projects.
    implementation(libs.coroutines.javafx)

    // Add binary dependencies for platform desired to support. Note that this
    // could also be broken out into package variants so that you aren't unnecessarily
    // including windows/macOS binaries in the .deb package, for example.
    val osName = System.getProperty("os.name")
    when {
        osName.contains("Windows", true) -> {
            implementation("io.matthewnelson.kotlin-components:kmp-tor-binary-mingwx64:${env.kmpTorBinaries.version.name}")
        }
        osName.contains("Mac", true) || osName.contains("Darwin", true) -> {
            implementation("io.matthewnelson.kotlin-components:kmp-tor-binary-macosx64:${env.kmpTorBinaries.version.name}")
        }
        osName.contains("linux", true) -> {
            implementation("io.matthewnelson.kotlin-components:kmp-tor-binary-linuxx64:${env.kmpTorBinaries.version.name}")
        }
        else -> {
            throw GradleException("Failed to determine Operating System from os.name='$osName'")
        }
    }

    // Add support for Unix Domain Sockets (Only necessary for JDK 15 and below)
    implementation("io.matthewnelson.kotlin-components:kmp-tor-ext-unix-socket:$vKmpTor")

    // Only supporting x86_64 (x64) for this sample
//  implementation("io.matthewnelson.kotlin-components:kmp-tor-binary-linuxx86:${env.kmpTorBinaries.version.name}")
//  implementation("io.matthewnelson.kotlin-components:kmp-tor-binary-macosarm64:${env.kmpTorBinaries.version.name}")
//  implementation("io.matthewnelson.kotlin-components:kmp-tor-binary-mingwx86:${env.kmpTorBinaries.version.name}")
}
