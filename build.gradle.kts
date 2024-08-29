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
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompileTool

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply(false)
    alias(libs.plugins.android.app) apply(false)
    alias(libs.plugins.android.library) apply(false)
    alias(libs.plugins.binary.compat)
    alias(libs.plugins.dokka)
}

allprojects {

    findProperty("GROUP")?.let { group = it }
    findProperty("VERSION_NAME")?.let { version = it }
    findProperty("POM_DESCRIPTION")?.let { description = it.toString() }

    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()

        if (version.toString().endsWith("-SNAPSHOT")) {
            // Only allow snapshot dependencies for non-release versions.
            // This would cause a build failure if attempting to make a release
            // while depending on a -SNAPSHOT version (such as core).
            maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
        }
    }

}

plugins.withType<YarnPlugin> {
    the<YarnRootExtension>().lockFileDirectory = rootDir.resolve(".kotlin-js-store")
}

apiValidation {
    if (findProperty("CHECK_PUBLICATION") != null) {
        ignoredProjects.add("check-publication")
    } else {
        nonPublicMarkers.add("io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi")
    }
}

/**
 * Corrects metadata manifest value for `unique_name` for all source sets
 * by ensuring it is prefixed with the [Project.getGroup] identifier.
 *
 * e.g. (Metadata manifest for module :runtime + source set commonMain)
 *
 *     // Before
 *     abi_version=1.8.0
 *     compiler_version=1.9.24
 *     ir_signature_versions=1,2
 *     metadata_version=1.4.1
 *     unique_name=runtime_commonMain
 *
 *     // After
 *     abi_version=1.8.0
 *     compiler_version=1.9.24
 *     ir_signature_versions=1,2
 *     metadata_version=1.4.1
 *     unique_name=io.matthewnelson.kmp-tor\:runtime_commonMain
 *
 * Kotlin `2.0.0+` uses the K2 compiler, so any dependency with a module name
 * of `runtime` will cause a conflict. For example, having `kmp-tor:runtime`
 * and `jetbrains.compose:runtime` dependencies for `commonMain` on the same
 * project would cause a build failure.
 *
 * Run `./gradlew metadataCommonMainClasses` and look at output for fixed fields.
 *
 * See: https://youtrack.jetbrains.com/issue/KT-66568/
 * */
@Suppress("RedundantSamConstructor")
rootProject.allprojects(Action {
    val project = this

    tasks.withType<KotlinCompileTool>().all compile@ {
        val task = this

        if (!task.name.startsWith("compile")) return@compile
        if (!task.name.endsWith("MainKotlinMetadata")) return@compile

        task.doLast {
            task.outputs.files.files.flatMap { output ->
                output.walkTopDown().mapNotNull { file ->
                    file.takeIf { it.isFile && it.name == "manifest" }
                }
            }.forEach { manifest ->
                val content = manifest.readLines().let { lines ->
                    val map = LinkedHashMap<String, String>(lines.size, 1.0f)
                    for (line in lines) {
                        if (line.isBlank()) continue

                        val iEq = line.indexOf('=')
                        if (iEq == -1) {
                            throw GradleException(
                                "Metadata manifest file contents invalid. " +
                                "Contains invalid key-value-pairs '$line'"
                            )
                        }
                        map[line.substring(0, iEq)] = line.substring(iEq + 1)
                    }
                    map
                }

                val old = content["unique_name"] ?: return@forEach

                // Has an actual unique_name value, such
                // as `io.matthewnelson.kmp-tor\:runtime_commonMain`
                //
                // if it did not, it would simply be `runtime_commonMain`
                if (old.contains(':')) return@forEach

                val groupId = project.group.toString()
                if (groupId.isBlank()) {
                    throw GradleException(
                        "Unable to fix metadata manifest 'unique_name' value of $old." +
                        " Project.group was blank."
                    )
                }

                if (groupId.indexOfFirst { it.isWhitespace() } != -1) {
                    throw GradleException(
                        "Unable to fix metadata manifest 'unique_name' value of $old." +
                        " Project.group contains whitespace"
                    )
                }

                val new = "$groupId\\:$old"

                val silence = "kotlin.mpp.silenceFixUniqueName"
                if (project.properties[silence] != "true") {
                    println("""
                        Kotlin Metadata 'unique_name' value fixed.
                        Old[$old] >> New[$new]
                        This message can be disabled by adding '$silence=true' to gradle.properties
                    """.trimIndent())
                }

                content["unique_name"] = new

                manifest.bufferedWriter().use { writer ->
                    content.entries.forEach { (key, value) ->
                        writer.write(key)
                        writer.write("=")
                        writer.write(value)
                        writer.newLine()
                    }
                }
            }
        }
    }
})
