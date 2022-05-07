rootProject.name = "kmp-tor"

includeBuild("kotlin-components/includeBuild/dependencies")
includeBuild("kotlin-components/includeBuild/kmp")

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

@Suppress("PrivatePropertyName")
private val KMP_TARGETS: String? by settings

private val allTargets = System.getProperty("KMP_TARGETS_ALL") != null
private val targets = KMP_TARGETS?.split(',')

if (allTargets || (targets?.contains("ANDROID") != false && targets?.contains("JVM") != false)) {
    include(":samples:java:android")
    include(":samples:kotlin:android")
}

if (allTargets || targets?.contains("JVM") != false) {
    include(":samples:kotlin:javafx")
}

@Suppress("PrivatePropertyName")
private val CHECK_PUBLICATION: String? by settings
if (CHECK_PUBLICATION != null) {
    include(":tools:check-publication")
}
