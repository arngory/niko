@file:Suppress("UnstableApiUsage")

import java.util.zip.ZipFile

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("com.google.devtools.ksp")
    id("kotlin-parcelize")
    id("app.cash.paparazzi")
    id("org.jetbrains.kotlin.plugin.compose")
}

setupApp()

// NOTICE of the repository's root in the APK as assets/NOTICE, beside assets/licenses/go.json that libcore.aar brings
// (buildScript/lib/core/licenses.py): one file, the same text in the repository and in the app
abstract class NoticeAsset : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val notice: RegularFileProperty

    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @TaskAction
    fun copy() {
        val dir = output.get().asFile
        dir.deleteRecursively()
        dir.mkdirs()
        notice.get().asFile.copyTo(dir.resolve("NOTICE"))
    }
}

val noticeAsset = tasks.register<NoticeAsset>("noticeAsset") {
    notice.set(rootProject.layout.projectDirectory.file("NOTICE"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(noticeAsset, NoticeAsset::output)
    }
}

// the golden fixtures the unit tests read (io.niko.golden, docs/design/desktop-stage3.md §5): a change to them, or the
// flag that writes them anew, runs the tests again instead of taking the last run as up to date
tasks.withType<Test>().configureEach {
    inputs.dir(rootProject.layout.projectDirectory.dir("shared/golden")).withPropertyName("golden")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.property("goldenUpdate", providers.environmentVariable("NIKO_GOLDEN_UPDATE").orElse(""))
}

android {
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
    ksp {
        arg("room.incremental", "true")
        arg("room.schemaLocation", "$projectDir/schemas")
    }
    bundle {
        language {
            enableSplit = false
        }
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
        aidl = true
        compose = true
    }
    namespace = "io.niko"
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
    androidResources {
        generateLocaleConfig = true
    }
}

dependencies {

    implementation(fileTree("libs"))
    testImplementation("junit:junit:4.13.2")
    // android.jar only has stubs of org.json
    testImplementation("org.json:json:20250517")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    // the screens drawn from the canvas (ui/compose, docs/research/ui-probe.md): foundation and ui only, no Material:
    // the Niko design system draws its own components
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.fragment:fragment-ktx:1.8.6")
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.navigation:navigation-fragment-ktx:2.8.9")
    implementation("androidx.navigation:navigation-ui-ktx:2.8.9")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.work:work-multiprocess:2.10.0")

    implementation("com.google.android.material:material:1.12.0")
    implementation("com.google.code.gson:gson:2.11.0")

    implementation("com.github.jenly1314:zxing-lite:2.1.1")
    implementation("com.blacksquircle.ui:editorkit:2.6.0")
    implementation("com.blacksquircle.ui:language-base:2.6.0")
    implementation("com.blacksquircle.ui:language-json:2.6.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.yaml:snakeyaml:1.33")
    implementation("com.jakewharton:process-phoenix:2.1.2")
    implementation("com.esotericsoftware:kryo:5.6.2")
    implementation("com.google.guava:guava:33.4.8-android")
    implementation("org.ini4j:ini4j:0.5.4")

    implementation("com.simplecityapps:recyclerview-fastscroll:2.0.1") {
        exclude(group = "androidx.recyclerview")
        exclude(group = "androidx.appcompat")
    }

    implementation("androidx.room:room-runtime:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("com.github.MatrixDev.Roomigrant:RoomigrantLib:0.3.4")
    ksp("com.github.MatrixDev.Roomigrant:RoomigrantCompiler:0.3.4")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.3")
}

// assets/licenses/components.json covers what the APK's code is built from besides the core: every module of the
// release's runtime classpath and of library desugaring is named by a component's `gradle` patterns, and every pattern
// still names one; every text a component cites is in assets/licenses/texts, and no other; a module whose Java
// resources carry a NOTICE (section 4(d) of the Apache License: the APK's packaging leaves META-INF/NOTICE out) is in
// a component's `notice_from`, whose texts quote it. The release's build runs it
tasks.register("checkLicenses") {
    group = "verification"
    description = "Every runtime module of the APK under a component of assets/licenses/components.json"
    val licenses = file("src/main/assets/licenses")
    inputs.dir(licenses)
    doLast {
        @Suppress("UNCHECKED_CAST")
        val components = (groovy.json.JsonSlurper().parse(licenses.resolve("components.json")) as Map<String, Any>)
            .getValue("components") as List<Map<String, Any>>
        fun strings(c: Map<String, Any>, key: String) = (c[key] as? List<*>).orEmpty().map { it as String }
        fun matches(pattern: String, module: String) =
            Regex(pattern.split("*").joinToString(".*") { Regex.escape(it) }).matches(module)

        val runtime = configurations.getByName("ossReleaseRuntimeClasspath")
        val modules = (listOf(runtime, configurations.getByName("coreLibraryDesugaring"))).flatMap { configuration ->
            configuration.incoming.resolutionResult.allComponents.mapNotNull { it.id as? ModuleComponentIdentifier }
        }.map { "${it.group}:${it.module}" }.toSortedSet()
        val patterns = components.flatMap { c -> strings(c, "gradle").map { it to c["name"] } }
        val problems = mutableListOf<String>()
        modules.filter { m -> patterns.none { matches(it.first, m) } }
            .forEach { problems += "$it: no component of components.json covers it" }
        patterns.filter { p -> modules.none { matches(p.first, it) } }
            .forEach { problems += "${it.second}: ${it.first} matches no runtime module" }

        val cited = components.flatMap { strings(it, "texts") }.toSet()
        val texts = licenses.resolve("texts").list().orEmpty().toSet()
        (cited - texts).forEach { problems += "texts/$it: cited, not there" }
        (texts - cited).forEach { problems += "texts/$it: cited by no component" }

        // the Java resources the APK's packaging merges: a jar's own, an AAR's classes.jar
        val javaRes = runtime.incoming.artifactView {
            attributes { attribute(Attribute.of("artifactType", String::class.java), "android-java-res") }
        }.artifacts
        val noticed = components.flatMap { strings(it, "notice_from") }.toSet()
        for (artifact in javaRes) {
            val id = artifact.id.componentIdentifier as? ModuleComponentIdentifier ?: continue
            val file = artifact.file
            val carries = if (file.isDirectory) {
                file.resolve("META-INF").listFiles().orEmpty().any { it.name.startsWith("NOTICE") }
            } else {
                ZipFile(file).use { zip ->
                    zip.entries().asSequence().any { it.name.startsWith("META-INF/NOTICE") }
                }
            }
            val module = "${id.group}:${id.module}"
            if (carries && module !in noticed) problems += "$module carries META-INF/NOTICE: quote it in a text and " +
                "name the module in that component's notice_from (and in NOTICE)"
        }
        noticed.filter { it !in modules }.forEach { problems += "notice_from $it: no such runtime module" }

        if (problems.isNotEmpty()) throw GradleException("assets/licenses:\n  " + problems.joinToString("\n  "))
        logger.lifecycle("assets/licenses: ${modules.size} modules under ${components.size} components, " +
            "${texts.size} texts")
    }
}
