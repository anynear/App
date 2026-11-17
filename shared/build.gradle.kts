// :shared — shared logic and Compose UI for Android and iOS.
//
//   ./gradlew :shared:testAndroidHostTest                  # unit tests (JVM)
//   ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64  # iOS framework (only with Xcode)

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

// Values from local.properties (stay local) or as Gradle property:
//   app.devRelayHost=192.168.178.20  machine with the Docker relays for real phones on Wi-Fi; empty: emulator/simulator only.
//   app.devNet=true                 local Docker network from Relay compose.net.yaml instead of the anynear network.
fun localProperty(name: String): Provider<String> = providers.gradleProperty(name).orElse(
    providers.fileContents(rootProject.layout.projectDirectory.file("local.properties")).asText.map { text ->
        text.lines().map { it.trim() }.firstOrNull { it.startsWith("$name=") }?.substringAfter("=")?.trim().orEmpty()
    }.orElse(""),
)
val devRelayHostProvider = localProperty("app.devRelayHost")
val devNetProvider = localProperty("app.devNet")
// Signed network list (ADR 0015, 0035 §6), taken over unchanged: the beta workflow stores it as net.json
// (release asset of the newest Relay release or https://anynear.net/andp/v1/net). Without the file the app builds
// with no embedded list and fetches it from the starting point on first start. The app checks the signature itself.
val embeddedNetList = providers.fileContents(rootProject.layout.projectDirectory.file("net.json")).asText.orElse("")

val generateBuildConfig by tasks.registering {
    val host = devRelayHostProvider
    val devNet = devNetProvider
    val netList = embeddedNetList
    val out = layout.buildDirectory.dir("generated/appBuildConfig/kotlin")
    inputs.property("devRelayHost", host)
    inputs.property("devNet", devNet)
    inputs.property("netList", netList)
    outputs.dir(out)
    doLast {
        val file = out.get().file("app/anynear/core/BuildNetConfig.kt").asFile
        file.parentFile.mkdirs()
        val list = netList.get().trim()
        require(!list.contains("\"\"\"") && !list.contains("$")) { "net.json enthält unerwartete Zeichen" }
        file.writeText(
            "package app.anynear.core\n\n" +
                "/** Aus `app.devRelayHost` (local.properties), erzeugt von shared/build.gradle.kts. */\n" +
                "internal const val CONFIGURED_DEV_RELAY_HOST: String = \"${host.get()}\"\n\n" +
                "/** `app.devNet=true`: lokales Docker-Netz statt des anynear-Netzes (nur Entwicklung). */\n" +
                "internal const val CONFIGURED_DEV_NET: Boolean = ${devNet.get() == "true"}\n\n" +
                "/** Eingebettete signierte network list aus net.json, leer ohne Datei. */\n" +
                "internal const val EMBEDDED_NET_LIST: String = \"\"\"" + list + "\"\"\"\n",
        )
    }
}

kotlin {
    jvmToolchain(21)

    android {
        namespace = "app.anynear.shared"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()
        withHostTest {}
        // Pack font and license texts from composeResources into the app.
        androidResources { enable = true }
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
            // The SDK's SQLDelight driver needs the system SQLite.
            linkerOpts("-lsqlite3")
        }
    }

    // meshtastic-sdk 0.2.0 reports its metadata as pre-release (see SDK sample app).
    targets.configureEach {
        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions {
                    freeCompilerArgs.add("-Xskip-prerelease-check")
                    freeCompilerArgs.add("-opt-in=kotlin.uuid.ExperimentalUuidApi")
                    freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
                }
            }
        }
    }

    sourceSets {
        commonMain.get().kotlin.srcDir(generateBuildConfig)
        commonMain.dependencies {
            implementation(libs.meshtastic.sdk.core)
            implementation(libs.meshtastic.sdk.transport.ble)
            implementation(libs.meshtastic.sdk.transport.tcp)
            implementation(libs.meshtastic.sdk.storage)
            implementation(libs.kable)
            implementation(libs.coroutines.core)
            implementation(libs.ktor.client.core)
            implementation(libs.serialization.json)
            // ANMP core on ANDP (ADR 0034–0036, 0061): brings the format (app.anynear.anmp) and the ANDP client along.
            implementation("app.anynear.anmp:core")
            implementation(libs.serialization.cbor)
            implementation(libs.cryptography.core)
            implementation(libs.qrose)

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.compose.components.resources)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            // Update radios (ADR 0056): nRF52 via DFU.
            api(libs.nordic.dfu)
            // Rescue via USB cable (ADR 0056).
            implementation(libs.usb.serial.android)
            // Read text from photos and scanned PDFs, on-device only (ADR 0067); :androidApp supplies language data.
            // Detect topics in photos, on-device only (ADR 0070); :androidApp supplies the model.
            implementation(libs.litert)
            // Back button in the chat (BackHandler).
            implementation(libs.androidx.activity.compose)
            // JDK crypto with BouncyCastle: Ed25519 is only in the Android JDK from API 33 on.
            implementation(libs.cryptography.provider.jdk)
            implementation(libs.bouncycastle)
            // Scan QR codes without Google services (ADR 0008): camera image via CameraX, reading with ZXing.
            implementation(libs.camerax.camera2)
            implementation(libs.camerax.lifecycle)
            implementation(libs.camerax.view)
            implementation(libs.zxing.core)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.cryptography.provider.optimal)
            implementation(libs.multiplatform.settings)
            // Swipe from the left edge = back (SystemBackHandler).
            implementation(libs.navigationevent.compose)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
        }
    }
}

compose.resources {
    packageOfResClass = "app.anynear.resources"
}

// Tests run in German, regardless of the machine's language; English is checked explicitly by the
// language tests (ADR 0026).
tasks.withType<Test>().configureEach {
    systemProperty("user.language", "de")
    systemProperty("user.country", "DE")
}
