rootProject.name = "kmp-tor"

pluginManagement {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }
}

includeBuild("build-logic")

includeBuild("kotlin-components/includeBuild/dependencies")
includeBuild("kotlin-components/includeBuild/kmp")

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
    include(":library:kmp-tor")
    include(":library:kmp-tor-common")
    include(":library:kmp-tor-internal")

    include(":library:controller:kmp-tor-controller")
    include(":library:controller:kmp-tor-controller-common")

    include(":library:manager:kmp-tor-manager")
    include(":library:manager:kmp-tor-manager-common")

    include(":library:extensions:kmp-tor-ext-callback-common")
    include(":library:extensions:kmp-tor-ext-callback-controller")
    include(":library:extensions:kmp-tor-ext-callback-controller-common")
    include(":library:extensions:kmp-tor-ext-callback-manager")
    include(":library:extensions:kmp-tor-ext-callback-manager-common")

    include(":library:extensions:kmp-tor-ext-unix-socket")

    if (KMP_TARGETS_ALL || (TARGETS?.contains("ANDROID") != false && TARGETS?.contains("JVM") != false)) {
        include(":samples:java:android")
        include(":samples:kotlin:android")
    }

    if (KMP_TARGETS_ALL || TARGETS?.contains("JVM") != false) {
        include(":samples:java:javafx")
        include(":samples:kotlin:javafx")
    }
}
