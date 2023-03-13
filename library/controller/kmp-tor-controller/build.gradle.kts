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

kmpConfiguration {
    configureShared(
        androidNameSpace = "io.matthewnelson.kmp.tor.controller",
        publish = !(env.kmpTorAll.isBinaryRelease || env.kmpTor.holdPublication)
    ) {
        androidLibrary {
            sourceSetMain {
                dependencies {
                    // https://github.com/Kotlin/kotlinx.atomicfu/issues/145
                    implementation(libs.atomicfu.jvm)
                }
            }
        }

        common {
            pluginIds(libs.plugins.atomicfu.get().pluginId)

            sourceSetMain {
                dependencies {
                    implementation(libs.encoding.base16)
                    implementation(libs.coroutines.core)
                    api(project(":library:controller:kmp-tor-controller-common"))
                }
            }
            sourceSetTest {
                dependencies {
                    implementation(libs.coroutines.test)
                }
            }
        }
    }
}
