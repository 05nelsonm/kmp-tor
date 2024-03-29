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

/**
 * Unfortunately, multiplatform setup for a project with both Android
 * and Jvm means the JavaPlugin is not able to be applied to the Jvm target.
 * So, we need to create a separate, non-android having module to consume Java
 * 11+ apis. This is also beneficial as `kmp-tor` library consumers needn't be
 * exposed as this dependency will be transitively provided.
 * */
kmpConfiguration {
    configureShared(
        publish = !(env.kmpTorAll.isBinaryRelease || env.kmpTor.holdPublication)
    ) {
        jvm {
            kotlinJvmTarget = JavaVersion.VERSION_11
            compileSourceCompatibility = JavaVersion.VERSION_11
            compileTargetCompatibility = JavaVersion.VERSION_11
        }
    }
}
