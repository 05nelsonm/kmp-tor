rootProject.name = "kmp-tor"

pluginManagement {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }
}

includeBuild("build-logic")

@Suppress("PrivatePropertyName")
private val CHECK_PUBLICATION: String? by settings

if (CHECK_PUBLICATION != null) {
    include(":tools:check-publication")
} else {
    listOf(
        "runtime",
        "runtime-api",
        "runtime-ctrl",
    ).forEach { name ->
        include(":library:$name")
    }
}
