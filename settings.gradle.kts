// the plugins' repositories as buildSrc/settings.gradle.kts has them: Central beside the plugin portal
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

include(":app")
rootProject.name = "NB4A"
