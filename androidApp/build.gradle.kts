import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipInputStream

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "app.anynear"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        // Fixed forever (ADR 0061, decisions 3 and 13): new app next to `com.pyrsa.pyrsa`, which starts from scratch.
        applicationId = "app.anynear"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        // CI sets both via -Papp.versionCode / -Papp.versionName (.github/workflows/beta-release.yml).
        versionCode = providers.gradleProperty("app.versionCode").orNull?.toInt() ?: 1
        versionName = providers.gradleProperty("app.versionName").orNull
            ?: providers.gradleProperty("app.versionBase").get()
        manifestPlaceholders["appLabel"] = "anynear"
    }

    // Release key only from the environment (GitHub Secrets), never in the repo. Without it the
    // release APK stays unsigned; locally assembleDirectDebug is enough.
    val keystore = providers.environmentVariable("KEYSTORE_FILE").orNull
    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = providers.environmentVariable("KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("KEY_PASSWORD").get()
            }
        }
    }


    // Test version next to the installed beta: ./gradlew assembleDirectDebug -Papp.parallelInstall=true
    // Own app ID (…anynear.test) and name „anynear Test“, hence its own setup and its own data.
    // Contact links still open the beta, because App Links are only verified for the release app.
    val parallelInstall = providers.gradleProperty("app.parallelInstall").orNull?.toBooleanStrict() ?: false
    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    // Two ways to the phone, the same app ID and the same key (so one can switch without losing data):
    // `direct` is the APK from the GitHub releases, it updates itself (AppUpdater) and keeps the permissions for that;
    // `play` goes to Google Play as an app bundle, Play updates it, and it leaves out what Play does not allow
    // (installing packages, the old app ID). See src/direct/AndroidManifest.xml.
    flavorDimensions += "store"
    productFlavors {
        create("direct") {
            dimension = "store"
            isDefault = true
            buildConfigField("boolean", "SELF_UPDATE", "true")
        }
        create("play") {
            dimension = "store"
            buildConfigField("boolean", "SELF_UPDATE", "false")
        }
    }

    buildTypes {
        getByName("debug") {
            if (parallelInstall) {
                applicationIdSuffix = ".test"
                versionNameSuffix = "-test"
                manifestPlaceholders["appLabel"] = "anynear Test"
            }
        }
        getByName("release") {
            isMinifyEnabled = false
            if (keystore != null) signingConfig = signingConfigs.getByName("release")
        }
    }
}

/**
 * Image model for topics in photos (ADR 0070): MobileNet v1, quantized, ImageNet (Apache-2.0), downloaded from the
 * TensorFlow archive and accepted only with the expected SHA-256. Not in the repo, but as an asset under `vision/`.
 */
abstract class VisionModelTask : DefaultTask() {
    @get:Input
    abstract val url: Property<String>

    @get:Input
    abstract val sha256: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun download() {
        val dir = outputDir.get().dir("vision").asFile.apply { mkdirs() }
        val target = File(dir, "mobilenet_v1_1.0_224_quant.tflite")
        val marker = File(dir, ".sha256")
        if (target.exists() && marker.exists() && marker.readText() == sha256.get()) return
        val zip = URI(url.get()).toURL().readBytes()
        val hash = MessageDigest.getInstance("SHA-256").digest(zip).joinToString("") { "%02x".format(it) }
        check(hash == sha256.get()) { "Bildmodell: unerwarteter SHA-256" }
        ZipInputStream(zip.inputStream()).use { input ->
            generateSequence { input.nextEntry }.first { it.name == target.name }
            target.writeBytes(input.readBytes())
        }
        marker.writeText(hash)
    }
}

val visionModel = tasks.register<VisionModelTask>("visionModel") {
    url.set("https://storage.googleapis.com/download.tensorflow.org/models/tflite/mobilenet_v1_1.0_224_quant_and_labels.zip")
    sha256.set("2f8054076cf655e1a73778a49bd8fd0306d32b290b7e576dda9574f00f186c0f")
    outputDir.set(layout.buildDirectory.dir("generated/vision"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(visionModel, VisionModelTask::outputDir)
    }
}

tasks.withType<KotlinCompilationTask<*>>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-Xskip-prerelease-check")
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.meshtastic.sdk.storage)
    implementation(libs.androidx.activity.compose)
    // Passkey provider from Android 14 on (ADR 0052).
    implementation(libs.androidx.credentials)
    // androidx.credentials pulls in an old Fragment; Lint otherwise aborts the release build
    // (InvalidFragmentVersionForActivityResult, registerForActivityResult needs Fragment 1.3.0 or later).
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.work)
    implementation(libs.serialization.json)
    // Google Play In-App Updates (Docs ADR 0096): only the Play build talks to Play; `direct` stays without it.
    "playImplementation"(libs.play.app.update.ktx)
}
