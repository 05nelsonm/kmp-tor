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
import kmp.tor.env

plugins {
    id("configuration")
}

includeStagingRepoIfTrue(env.kmpTorBinaries.pollStagingRepo)

// Override
ext.set("VERSION_NAME", env.kmpTorAll.version.name)
ext.set("VERSION_CODE", env.kmpTorAll.version.code)

kmpConfiguration {
    configureShared(
        androidNameSpace = "io.matthewnelson.kmp.tor",
        publish = !(env.kmpTorAll.holdPublication),
    ) {
        androidLibrary {
            android {
                defaultConfig {
                    minSdk = 21
                }
            }
            sourceSetMain {
                dependencies {
                    implementation("$group:kmp-tor-binary-android:${env.kmpTorBinaries.version.name}")
                }
            }
        }

        jvm {
            sourceSetTest {
                dependencies {
                    if (System.getProperty("java.version").substringBefore('.').toInt() < 16) {
                        // Use unix socket factory library
                        if (env.kmpTorAll.isBinaryRelease) {
                            implementation("$group:kmp-tor-ext-unix-socket:${env.kmpTor.version.name}")
                        } else {
                            implementation(project(":library:extensions:kmp-tor-ext-unix-socket"))
                        }
                    }

                    implementation("$group:kmp-tor-binary-linuxx64:${env.kmpTorBinaries.version.name}")
                    implementation("$group:kmp-tor-binary-linuxx86:${env.kmpTorBinaries.version.name}")
                    implementation("$group:kmp-tor-binary-macosx64:${env.kmpTorBinaries.version.name}")
                    implementation("$group:kmp-tor-binary-mingwx64:${env.kmpTorBinaries.version.name}")
                    implementation("$group:kmp-tor-binary-mingwx86:${env.kmpTorBinaries.version.name}")
                }
            }

            // Requires Java 11+ b/c of java.lang.management.ManagementFactory
            // to retrieve process id.
            kotlinJvmTarget = JavaVersion.VERSION_11
            compileSourceCompatibility = JavaVersion.VERSION_11
            compileTargetCompatibility = JavaVersion.VERSION_11
        }

        common {
            sourceSetMain {
                dependencies {
                    implementation(libs.coroutines.core)
                    implementation("$group:kmp-tor-binary-geoip:${env.kmpTorBinaries.version.name}")
                    implementation("$group:kmp-tor-binary-extract:${env.kmpTorBinaries.version.name}")

                    if (env.kmpTorAll.isBinaryRelease) {
                        api("$group:kmp-tor-manager:${env.kmpTor.version.name}")
                    } else {
                        api(project(":library:manager:kmp-tor-manager"))
                    }
                }
            }
            sourceSetTest {
                dependencies {
                    implementation(libs.coroutines.test)

                    if (env.kmpTorAll.isBinaryRelease) {
                        implementation("$group:kmp-tor-ext-callback-manager:${env.kmpTor.version.name}")
                    } else {
                        implementation(project(":library:extensions:kmp-tor-ext-callback-manager"))
                    }
                }
            }
        }
    }
}
