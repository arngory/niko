// kotlin-dsl and its Kotlin artifacts: Maven Central beside the plugin portal, so a portal that loses them for a
// moment (it only redirects to Central) does not fail the build
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
