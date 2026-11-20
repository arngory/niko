plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
}

apply(from = "../repositories.gradle.kts")

dependencies {
    // Gradle Plugins
    implementation("com.android.tools.build:gradle:8.8.1")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21")
    // Compose's compiler, as Kotlin 2 ships it (the screens drawn from the canvas, docs/research/ui-probe.md)
    implementation("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.0.21")
    // the Niko theme's parts as images in JVM tests (app/src/test/snapshots), no emulator
    implementation("app.cash.paparazzi:paparazzi-gradle-plugin:1.3.5")
}
