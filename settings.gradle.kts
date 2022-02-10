rootProject.name = "kmp-tor"

includeBuild("kotlin-components/includeBuild/dependencies")
includeBuild("kotlin-components/includeBuild/kmp")

include(":library:kmp-tor")
include(":library:kmp-tor-common")

include(":library:controller:kmp-tor-controller")
include(":library:controller:kmp-tor-controller-common")

include(":library:manager:kmp-tor-manager")
include(":library:manager:kmp-tor-manager-common")

@Suppress("PrivatePropertyName")
private val KMP_TARGETS: String? by settings
@Suppress("PrivatePropertyName")
private val KMP_TARGETS_ALL: String? by settings

// if ANDROID or JVM is not being built, don't include the android sample
if (
    KMP_TARGETS_ALL != null ||
    (KMP_TARGETS?.split(',')?.contains("ANDROID") != false &&
    KMP_TARGETS?.split(',')?.contains("JVM") != false)
) {
    include(":samples:android")
}

// if JVM is not being built, don't include the javafx sample
if (
    KMP_TARGETS_ALL != null ||
    KMP_TARGETS?.split(',')?.contains("JVM") != false
) {
    include(":samples:javafx")
}

@Suppress("PrivatePropertyName")
private val CHECK_PUBLICATION: String? by settings
if (CHECK_PUBLICATION != null) {
    include(":tools:check-publication")
}
