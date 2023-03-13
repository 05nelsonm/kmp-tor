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

// TODO: Move to build-environment
repositories {
    val host = "https://s01.oss.sonatype.org"

    if (version.toString().endsWith("-SNAPSHOT")) {
        maven("$host/content/repositories/snapshots/")
    } else {
        maven("$host/content/groups/staging") {
            val p = rootProject.properties

            credentials {
                username = p["mavenCentralUsername"]?.toString()
                password = p["mavenCentralPassword"]?.toString()
            }
        }
    }
}

kmpConfiguration {
    configureShared(
        androidNameSpace = "io.matthewnelson.kmp.tor.tools.check.publication",
        isCommonModule = true,
    ) {
        androidLibrary {
            android {
                defaultConfig {
                    minSdk = 21
                }
            }
            sourceSetMain {
                dependencies {
                    implementation("$group:kmp-tor-android:${env.kmpTorAll.version.name}")
                    implementation("$group:kmp-tor-common-android:${env.kmpTor.version.name}")

                    implementation("$group:kmp-tor-controller-android:${env.kmpTor.version.name}")
                    implementation("$group:kmp-tor-controller-common-android:${env.kmpTor.version.name}")

                    implementation("$group:kmp-tor-ext-callback-controller-android:${env.kmpTor.version.name}")
                    implementation("$group:kmp-tor-ext-callback-controller-common-android:${env.kmpTor.version.name}")

                    implementation("$group:kmp-tor-ext-callback-manager-android:${env.kmpTor.version.name}")
                    implementation("$group:kmp-tor-ext-callback-manager-common-android:${env.kmpTor.version.name}")

                    implementation("$group:kmp-tor-manager-android:${env.kmpTor.version.name}")
                    implementation("$group:kmp-tor-manager-common-android:${env.kmpTor.version.name}")
                }
            }
        }

        jvm {
            sourceSetMain {
                dependencies {
                    implementation("$group:kmp-tor-jvm:${env.kmpTorAll.version.name}")
                    implementation("$group:kmp-tor-common-jvm:${env.kmpTor.version.name}")
                    implementation("$group:kmp-tor-internal-jvm:${env.kmpTor.version.name}")

                    implementation("$group:kmp-tor-controller-jvm:${env.kmpTor.version.name}")
                    implementation("$group:kmp-tor-controller-common-jvm:${env.kmpTor.version.name}")

                    implementation("$group:kmp-tor-ext-callback-controller-jvm:${env.kmpTor.version.name}")
                    implementation("$group:kmp-tor-ext-callback-controller-common-jvm:${env.kmpTor.version.name}")

                    implementation("$group:kmp-tor-ext-callback-manager-jvm:${env.kmpTor.version.name}")
                    implementation("$group:kmp-tor-ext-callback-manager-common-jvm:${env.kmpTor.version.name}")

                    implementation("$group:kmp-tor-ext-unix-socket-jvm:${env.kmpTor.version.name}")

                    implementation("$group:kmp-tor-manager-jvm:${env.kmpTor.version.name}")
                    implementation("$group:kmp-tor-manager-common-jvm:${env.kmpTor.version.name}")
                }
            }
        }

        common {
            sourceSetMain {
                dependencies {
                    implementation("$group:kmp-tor-common:${env.kmpTor.version.name}")
                    implementation("$group:kmp-tor-controller-common:${env.kmpTor.version.name}")
                    implementation("$group:kmp-tor-ext-callback-common:${env.kmpTor.version.name}")
                    implementation("$group:kmp-tor-ext-callback-controller-common:${env.kmpTor.version.name}")
                    implementation("$group:kmp-tor-ext-callback-manager-common:${env.kmpTor.version.name}")
                    implementation("$group:kmp-tor-manager-common:${env.kmpTor.version.name}")
                }
            }
        }
    }
}
