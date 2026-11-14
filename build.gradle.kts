plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinSerialization) apply false
}

// Abhängigkeiten festhalten (gradle.lockfile je Projekt), damit der Schwachstellen-Scan auch indirekte Abhängigkeiten
// sieht und Builds wiederholbar sind (ADR 0102). LENIENT: fehlende Einträge brechen den Build nicht ab.
// Neu schreiben: ./gradlew resolveAndLockAll --write-locks
allprojects {
    dependencyLocking {
        lockAllConfigurations()
        lockMode.set(LockMode.LENIENT)
    }
}

allprojects {
    tasks.register("resolveAndLockAll") {
        notCompatibleWithConfigurationCache("löst alle Konfigurationen auf")
        doFirst { require(gradle.startParameter.isWriteDependencyLocks) { "nur mit --write-locks aufrufen" } }
        doLast {
            configurations.filter { it.isCanBeResolved }.forEach { c ->
                runCatching { c.resolvedConfiguration.lenientConfiguration.artifacts }
                    .onFailure { logger.warn("${project.path}:${c.name} nicht auflösbar: ${it.message?.lineSequence()?.first()}") }
            }
        }
    }
}
