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
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
import org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED
import org.gradle.api.tasks.testing.logging.TestLogEvent.STARTED

// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {

    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }

    dependencies {
        classpath(io.matthewnelson.kotlin.components.dependencies.plugins.android.gradle)
        classpath(io.matthewnelson.kotlin.components.dependencies.plugins.kotlin.gradle)
        classpath(io.matthewnelson.kotlin.components.dependencies.plugins.kotlin.atomicfu)
        classpath(io.matthewnelson.kotlin.components.dependencies.plugins.mavenPublish)

        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle.kts files
    }
}

allprojects {

    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }

    tasks.withType<Test> {
        testLogging {
            exceptionFormat = TestExceptionFormat.FULL
            events(STARTED, PASSED, SKIPPED, FAILED)
            showStandardStreams = true
        }
    }

}

plugins {
    val vBinaryCompat = io.matthewnelson.kotlin.components.dependencies.versions.gradle.binaryCompat

    id(pluginId.kmp.publish)
    id(pluginId.kotlin.binaryCompat) version(vBinaryCompat)
}

kmpPublish {
    setupRootProject(
        versionName = env.kmpTor.version.name,
        versionCode = env.kmpTor.version.code,
        pomInceptionYear = 2021,
    )
}

@Suppress("LocalVariableName")
apiValidation {
    val KMP_TARGETS = findProperty("KMP_TARGETS") as? String
    val CHECK_PUBLICATION = findProperty("CHECK_PUBLICATION") as? String
    val KMP_TARGETS_ALL = System.getProperty("KMP_TARGETS_ALL") != null
    val TARGETS = KMP_TARGETS?.split(',')

    if (CHECK_PUBLICATION != null) {
        ignoredProjects.add("check-publication")
    } else {
        nonPublicMarkers.add("io.matthewnelson.kmp.tor.common.annotation.InternalTorApi")

        if (
            KMP_TARGETS_ALL ||
            (TARGETS?.contains("ANDROID") != false && TARGETS?.contains("JVM") != false)
        ) {
            ignoredProjects.add("android")
        }

        if (KMP_TARGETS_ALL || TARGETS?.contains("JVM") != false) {
            ignoredProjects.add("javafx")
        }
    }
}
