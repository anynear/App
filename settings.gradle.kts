pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Only usb-serial-for-android (rescue via USB, ADR 0056) is on JitPack alone; nothing else from there.
        exclusiveContent {
            forRepository { maven("https://jitpack.io") }
            filter {
                includeGroup("com.github.mik3y")
            }
        }
    }
}

rootProject.name = "anynear"

include(":shared")
include(":androidApp")

// ANMP core and format (ADR 0036, ADR 0061) via composite build from the neighbouring repository ANMP; ANMP includes
// ANDP itself (../../ANDP/kotlin relative to ANMP/kotlin, else -Pandp.dir or ANDP_DIR). Path to ANMP/kotlin: Gradle
// property `anmp.dir` or env variable ANMP_DIR; else ../ANMP (main checkout) or ../../ANMP (worktree in _worktrees/).
val anmpDir = providers.gradleProperty("anmp.dir").orNull
    ?: providers.environmentVariable("ANMP_DIR").orNull
    ?: listOf("../ANMP/kotlin", "../../ANMP/kotlin")
        .firstOrNull { settingsDir.resolve(it).resolve("settings.gradle.kts").exists() }
    ?: error("ANMP nicht gefunden: ANMP_DIR auf ANMP/kotlin setzen (Nachbarordner ../ANMP oder ../../ANMP)")
includeBuild(anmpDir) {
    // Both repos have their build in the folder `kotlin`; own name so that the builds do not look alike.
    name = "anmp"
    dependencySubstitution {
        substitute(module("app.anynear.anmp:format")).using(project(":format"))
        substitute(module("app.anynear.anmp:core")).using(project(":core"))
    }
}
