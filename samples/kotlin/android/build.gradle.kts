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
import io.matthewnelson.kotlin.components.dependencies.versions
import io.matthewnelson.kotlin.components.kmp.util.includeSnapshotsRepoIfTrue
import io.matthewnelson.kotlin.components.kmp.util.includeStagingRepoIfTrue
import kmp.tor.env

plugins {
    id(pluginId.android.application)
    id(pluginId.kotlin.android)
}

// disregard. this is for playing with newly published binaries prior to release
includeStagingRepoIfTrue(env.kmpTorBinaries.pollStagingRepo)

// For SNAPSHOTS
includeSnapshotsRepoIfTrue(true)

android {
    compileSdk = versions.android.sdkCompile
    buildToolsVersion = versions.android.buildTools
    namespace = "io.matthewnelson.kmp.tor.sample.kotlin.android"

    packagingOptions {
        // Needed for Tor binary file extraction to nativeDir
        jniLibs.useLegacyPackaging = true

        resources.excludes.add("META-INF/gradle/incremental.annotation.processors")
    }

    buildFeatures.viewBinding = true
    defaultConfig {
        applicationId = "io.matthewnelson.kmp.tor.sample.kotlin"
        minSdk = versions.android.sdkMin21
        targetSdk = versions.android.sdkTarget
        versionCode = env.kmpTorAll.version.code
        versionName = env.kmpTorAll.version.name

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["disableAnalytics"] = "true"
    }

    // Gradle 4.0's introduction of Google analytics to Android App Developers.
    // https://developer.android.com/studio/releases/gradle-plugin
    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
    }

    splits {

        // Configures multiple APKs based on ABI. This helps keep the size
        // down, since PT binaries can be large.
        abi {

            // Enables building multiple APKs per ABI.
            isEnable = true

            // By default all ABIs are included, so use reset() and include to specify
            // that we only want APKs for x86 and x86_64, armeabi-v7a, and arm64-v8a.

            // Resets the list of ABIs that Gradle should create APKs for to none.
            reset()

            // Specifies a list of ABIs that Gradle should create APKs for.
            include("x86", "armeabi-v7a", "arm64-v8a", "x86_64")

            // Specify whether or not you wish to also generate a universal APK that
            // includes _all_ ABIs.
            isUniversalApk = true
        }
    }
}

dependencies {
    implementation(deps.androidx.appCompat)
    implementation(deps.androidx.constraintLayout)
    implementation(deps.androidx.lifecycle.viewModel)
    implementation(deps.viewBindingDelegateNoReflect)

    // For SNAPSHOTS
//    implementation("io.matthewnelson.kotlin-components:kmp-tor:${env.kmpTorAll.version.name}")
    implementation(project(":library:kmp-tor"))

    implementation(deps.kotlin.coroutines.android)
}
