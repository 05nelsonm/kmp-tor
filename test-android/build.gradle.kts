/*
 * Copyright (c) 2025 Matthew Nelson
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
import com.android.build.gradle.tasks.MergeSourceSetFolders

plugins {
    id("configuration")
}

kmpConfiguration {
    configure {
        val jniLibsDir = projectDir
            .resolve("src")
            .resolve("androidInstrumentedTest")
            .resolve("jniLibs")

        project.tasks.all {
            if (name != "clean") return@all
            doLast { jniLibsDir.deleteRecursively() }
        }

        androidLibrary(namespace = "io.matthewnelson.kmp.tor.test.android", minSdk = 21) {
            android {
                defaultConfig {
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
                sourceSets["androidTest"].jniLibs.srcDir(jniLibsDir)
            }

            sourceSetTestInstrumented {
                dependencies {
                    implementation(libs.androidx.test.core)
                    implementation(libs.androidx.test.runner)
                    implementation(libs.kmp.process)
                    implementation(libs.kmp.tor.resource.compilation.exec.tor)
                }
            }
        }

        common {
            sourceSetTest {
                dependencies {
                    implementation(kotlin("test"))
                }
            }
        }

        kotlin {
            if (!project.plugins.hasPlugin("com.android.base")) return@kotlin

            val projectTests = listOf(
                ":library:runtime"      to "libTestRuntime.so",
                ":library:runtime-core" to "libTestRuntimeCore.so",
                ":library:runtime-ctrl" to "libTestRuntimeCtrl.so",
            ).map { (path, libName) ->
                try {
                    project.evaluationDependsOn(path)
                } catch (_: Throwable) {}

                project(path) to libName
            }

            project.afterEvaluate {
                val nativeTestBinaryTasks = projectTests.flatMap { (targetProject, libName) ->

                    val targetProjectBuildDir = targetProject
                        .layout
                        .buildDirectory
                        .asFile.get()

                    listOf(
                        "Arm32" to "armeabi-v7a",
                        "Arm64" to "arm64-v8a",
                        "X64" to "x86_64",
                        "X86" to "x86",
                    ).mapNotNull { (arch, abi) ->
                        val nativeTestBinariesTask = targetProject
                            .tasks
                            .findByName("androidNative${arch}TestBinaries")
                            ?: return@mapNotNull null

                        val abiDir = jniLibsDir.resolve(abi)
                        if (!abiDir.exists() && !abiDir.mkdirs()) throw RuntimeException("mkdirs[$abiDir]")

                        val testExecutable = targetProjectBuildDir
                            .resolve("bin")
                            .resolve("androidNative$arch")
                            .resolve("debugTest")
                            .resolve("test.kexe")

                        nativeTestBinariesTask.doLast {
                            testExecutable.copyTo(abiDir.resolve(libName), overwrite = true)
                        }

                        nativeTestBinariesTask
                    }
                }

                project.tasks.withType(MergeSourceSetFolders::class.java).all {
                    if (name != "mergeDebugAndroidTestJniLibFolders") return@all
                    nativeTestBinaryTasks.forEach { task -> dependsOn(task) }
                }
            }
        }
    }
}
