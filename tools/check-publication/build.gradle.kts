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
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

plugins {
    id("configuration")
}

repositories {
    google()

    if (version.toString().endsWith("-SNAPSHOT")) {
        maven("https://central.sonatype.com/repository/maven-snapshots/")
    } else {
        maven("https://central.sonatype.com/api/v1/publisher/deployments/download/") {
            val p = rootProject.properties
            authentication.create("Authorization", HttpHeaderAuthentication::class.java)
            credentials(HttpHeaderCredentials::class.java) {
                val username = p["mavenCentralUsername"]?.toString() ?: throw NullPointerException()
                val password = p["mavenCentralPassword"]?.toString() ?: throw NullPointerException()
                name = "Authorization"
                @OptIn(ExperimentalEncodingApi::class)
                value = "Bearer " + Base64.Mime.encode("$username:$password".encodeToByteArray())
            }
        }
    }
}

kmpConfiguration {
    configureShared(androidNamespace = "tools.check.publication") {
        androidLibrary {
            sourceSetMain {
                dependencies {
                    implementation("$group:runtime-service:$version")
                    implementation("$group:runtime-service-ui:$version")
                }
            }
        }

        common {
            sourceSetMain {
                dependencies {
                    implementation("$group:runtime:$version")
                    implementation("$group:runtime-core:$version")
                    implementation("$group:runtime-ctrl:$version")
                }
            }
        }

        kotlin {
            sourceSets.findByName("iosMain")?.dependencies {
                implementation("$group:runtime-service:$version")
                implementation("$group:runtime-service-ui:$version")
            }
        }
    }
}
