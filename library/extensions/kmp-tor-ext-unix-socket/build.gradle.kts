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
import io.matthewnelson.kotlin.components.kmp.KmpTarget
import kmp.tor.env

plugins {
    id(pluginId.kmp.configuration)
    id(pluginId.kmp.publish)
}

kmpConfiguration {
    setupMultiplatform(targets =
        setOf(

            KmpTarget.Jvm.Jvm(
                target = {
                    withJava()
                },
                mainSourceSet = {
                    dependencies {
                        implementation(deps.jnrUnixSocket)
                    }
                }
            ),

        ),
    )
}

kmpPublish {
    setupModule(
        pomDescription = "Kotlin Components' UnixSocket extension for JDK",
        holdPublication = env.kmpTorAll.isBinaryRelease || env.kmpTor.holdPublication
    )
}
