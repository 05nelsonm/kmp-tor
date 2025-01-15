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
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnLockMismatchReport
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    alias(libs.plugins.android.app) apply(false)
    alias(libs.plugins.android.library) apply(false)
    alias(libs.plugins.binary.compat)
    alias(libs.plugins.dokka)
    alias(libs.plugins.kotlin.multiplatform) apply(false)
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

@Suppress("PropertyName")
val CHECK_PUBLICATION = findProperty("CHECK_PUBLICATION") != null

plugins.withType<YarnPlugin> {
    the<YarnRootExtension>().lockFileDirectory = rootDir.resolve(".kotlin-js-store")
    if (CHECK_PUBLICATION) {
        the<YarnRootExtension>().yarnLockMismatchReport = YarnLockMismatchReport.NONE
    }
}

apiValidation {
    // :runtime-service & :runtime-service-ui modules are problematic on
    // non-macOS hosts because only iOS is enabled for native there.
    @OptIn(kotlinx.validation.ExperimentalBCVApi::class)
    klib.enabled = HostManager.hostIsMac && findProperty("KMP_TARGETS") == null

    if (CHECK_PUBLICATION) {
        ignoredProjects.add("check-publication")
    } else {
        nonPublicMarkers.add("io.matthewnelson.kmp.tor.common.api.InternalKmpTorApi")
    }
}
