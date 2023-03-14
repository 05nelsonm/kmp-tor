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

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>() {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_11.toString()
}

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    id(libs.plugins.multiplatform.get().pluginId)
    application
    alias(libs.plugins.javafx)
}

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
    mainClass.set("io.matthewnelson.kmp.tor.sample.kotlin.javafx.SampleApp")
}

javafx {
    modules("javafx.controls", "javafx.graphics")
}

// In order to import the `-jvm` variant of `project(":library:kmp-tor")`, we
// unfortunately need to set up this sample as a multiplatform project. This
// is attributed to the `kmp-tor` project utilizing certain gradle.properties,
// along with the `kmp-tor` module having an `-android` target available.
//
// Java projects importing the MavenCentral dependency do not
// need to do this, as it will import the appropriate -jvm variant for you.
kotlin {
    jvm {
        withJava()
    }

    with(sourceSets) {
        getByName("jvmMain") {
            dependencies {

                implementation(libs.tornadofx)

                // Add the javafx coroutine dependency so that we have
                // `Dispatchers.Main.immediate` support
                implementation(libs.coroutines.javafx)

                // For SNAPSHOTS
//                implementation("io.matthewnelson.kotlin-components:kmp-tor:${env.kmpTorAll.version.name}")
                implementation(project(":library:kmp-tor"))

                // Add binary dependencies for platform desired to support. Note that this
                // could also be broken out into package variants so that you aren't unnecessarily
                // including windows/macOS binaries in the .deb package, for example.
                val osName = System.getProperty("os.name")
                when {
                    osName.contains("Windows", true) -> {
                        implementation("io.matthewnelson.kotlin-components:kmp-tor-binary-mingwx64:${env.kmpTorBinaries.version.name}")
                    }
                    osName == "Mac OS X" -> {
                        implementation("io.matthewnelson.kotlin-components:kmp-tor-binary-macosx64:${env.kmpTorBinaries.version.name}")
                    }
                    osName.contains("Mac", true) -> {
                        // Will be providing our own binary resources for arm64 Tor 0.4.7.12 as an example
                        // which are located in resources/kmptor/macos/arm64 of this sample.
//                        implementation("io.matthewnelson.kotlin-components:kmp-tor-binary-macosx64:${env.kmpTorBinaries.version.name}")
                    }
                    osName.contains("linux", true) -> {
                        implementation("io.matthewnelson.kotlin-components:kmp-tor-binary-linuxx64:${env.kmpTorBinaries.version.name}")
                    }
                    else -> {
                        throw GradleException("Failed to determine Operating System from os.name='$osName'")
                    }
                }

                // In order to model our binary resources for macOS arm64, will need the extract
                // dependency (which is not provided with the kmp-tor import) to be able to use
                // the TorBinaryResource class
                implementation("io.matthewnelson.kotlin-components:kmp-tor-binary-extract:${env.kmpTorBinaries.version.name}")

                // Add support for Unix Domain Sockets (Only necessary for JDK 15 and below)
                implementation(project(":library:extensions:kmp-tor-ext-unix-socket"))

                // Only supporting x86_64 (x64) for this sample
//                implementation("io.matthewnelson.kotlin-components:kmp-tor-binary-linuxx86:${env.kmpTorBinaries.version.name}")
//                implementation("io.matthewnelson.kotlin-components:kmp-tor-binary-macosarm64:${env.kmpTorBinaries.version.name}")
//                implementation("io.matthewnelson.kotlin-components:kmp-tor-binary-mingwx86:${env.kmpTorBinaries.version.name}")
            }
        }
    }
}
