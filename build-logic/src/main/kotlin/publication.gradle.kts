/*
 * Copyright (c) 2023 Matthew Nelson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
import org.gradle.plugins.signing.SigningExtension

plugins {
    id("com.vanniktech.maven.publish")
}

if (!version.toString().endsWith("-SNAPSHOT")) {
    extensions.configure<SigningExtension>("signing") {
        useGpgCmd()
    }
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

// Hooks into project's metadata compilation task to ensure that the `unique_name`
// attribute is properly prefixed with the project group name, instead of just the
// `{module name}_sourceSet`
//
// Requires that `GROUP` is set in gradle.properties, or that project.group is set.
//
// Enable debug output by adding the following to root-project gradle.properties:
//
//     kotlin.mpp.fixMetadataUniqueNameDebug=true
//
// https://youtrack.jetbrains.com/issue/KT-66568/w-KLIB-resolver-The-same-uniquename...-found-in-more-than-one-library
tasks.all {
    if (!name.startsWith("compile")) return@all
    if (!name.endsWith("MainKotlinMetadata")) return@all

    // Take gradle.properties `GROUP`, otherwise use `project.group`
    val groupName = properties["GROUP"]
        ?.toString()
        ?.ifBlank { null }
        ?: project.group.toString()
            .ifBlank { null }

    if (groupName.isNullOrBlank()) {
        throw GradleException("group cannot be null or blank")
    }

    doLast {
        outputs.files.files.forEach out@ { output ->
            val manifests = output.walkTopDown().mapNotNull { file ->
                file.takeIf { it.name == "manifest" }
            }
            manifests.forEach { manifest ->
                val lines = manifest.readLines()
                val map = LinkedHashMap<String, String>(lines.size, 1.0f)

                for (line in lines) {
                    val i = line.indexOf('=')
                    if (i == -1) continue
                    map[line.substring(0, i)] = line.substring(i + 1)
                }

                val current = map["unique_name"] ?: return@forEach
                if (current.startsWith(groupName)) return@forEach
                val new = "$groupName\\:$current"

                if (properties["kotlin.mpp.fixMetadataUniqueNameDebug"] == "true") {
                    println("""
                        Misconfigured unique_name metadata attribute
                        for $manifest
                        Changing from '$current' to '$new'
                    """.trimIndent())
                }

                map["unique_name"] = new

                manifest.bufferedWriter().use { writer ->
                    map.entries.forEach { (key, value) ->
                        writer.write(key)
                        writer.write("=")
                        writer.write(value)
                        writer.newLine()
                    }
                }
            }
        }
    }
}
