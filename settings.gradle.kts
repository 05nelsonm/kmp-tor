rootProject.name = "kmp-tor"

includeBuild("kotlin-components/includeBuild/dependencies")
includeBuild("kotlin-components/includeBuild/kmp")

include(":library:kmp-tor")
include(":library:kmp-tor-common")

include(":library:controller:kmp-tor-controller")
include(":library:controller:kmp-tor-controller-common")

include(":library:manager:kmp-tor-manager")
include(":library:manager:kmp-tor-manager-common")

// if ANDROID or JVM is not being built, don't include the app
@Suppress("PrivatePropertyName")
private val KMP_TARGETS: String? by settings
@Suppress("PrivatePropertyName")
private val KMP_TARGETS_ALL: String? by settings
if (
    KMP_TARGETS_ALL != null ||
    (KMP_TARGETS?.split(',')?.contains("ANDROID") != false &&
    KMP_TARGETS?.split(',')?.contains("JVM") != false)
) {
    include(":samples:android")
}

@Suppress("PrivatePropertyName")
private val CHECK_PUBLICATION: String? by settings
if (CHECK_PUBLICATION != null) {
    include(":tools:check-publication")
}
