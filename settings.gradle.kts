rootProject.name = "kmp-tor"

pluginManagement {
    resolutionStrategy {
        eachPlugin {
            requested.version?.let { version ->
                when ("${requested.id}") {
                    "com.android.library",
                    "com.android.application" -> {
                        useModule("com.android.tools.build:gradle:$version")
                    }
                    "kotlinx-atomicfu" -> {
                        useModule("org.jetbrains.kotlinx:atomicfu-gradle-plugin:$version")
                    }
                }
            }
        }
    }
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }
}

includeBuild("build-logic")

//includeBuild("kotlin-components/includeBuild/dependencies")
//includeBuild("kotlin-components/includeBuild/kmp")

@Suppress("PrivatePropertyName")
private val KMP_TARGETS: String? by settings
@Suppress("PrivatePropertyName")
private val CHECK_PUBLICATION: String? by settings
@Suppress("PrivatePropertyName")
private val KMP_TARGETS_ALL = System.getProperty("KMP_TARGETS_ALL") != null
@Suppress("PrivatePropertyName")
private val TARGETS = KMP_TARGETS?.split(',')

if (CHECK_PUBLICATION != null) {
    include(":tools:check-publication")
} else {
    listOf(
        "kmp-tor",
        "kmp-tor-common",
        "kmp-tor-internal",
        "controller:kmp-tor-controller",
        "controller:kmp-tor-controller-common",
        "manager:kmp-tor-manager",
        "manager:kmp-tor-manager-common",
        "extensions:kmp-tor-ext-callback-common",
        "extensions:kmp-tor-ext-callback-controller",
        "extensions:kmp-tor-ext-callback-controller-common",
        "extensions:kmp-tor-ext-callback-manager",
        "extensions:kmp-tor-ext-callback-manager-common",
        "extensions:kmp-tor-ext-unix-socket",
    ).forEach { name ->
        include(":library:$name")
    }

    if (KMP_TARGETS_ALL || TARGETS?.contains("ANDROID") != false) {
        include(":samples:java:android")
        include(":samples:kotlin:android")
    }

    if (KMP_TARGETS_ALL || TARGETS?.contains("JVM") != false) {
        include(":samples:java:javafx")
        include(":samples:kotlin:javafx")
    }
}
