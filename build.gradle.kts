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

// Indirekte Abhängigkeiten mit bekannten Lücken auf korrigierte Versionen heben (ADR 0102), auch in Werkzeugen wie
// Lint. Meldet der nächtliche Scan eine neue Lücke in einer indirekten Abhängigkeit, hier ergänzen und die Lockfiles
// mit ./gradlew resolveAndLockAll --write-locks neu schreiben.
fun olderThan(version: String?, fixed: String): Boolean {
    val a = version?.substringBefore('-')?.split('.')?.map { it.toIntOrNull() ?: 0 } ?: return false
    val b = fixed.split('.').map { it.toInt() }
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
        if (x != y) return x < y
    }
    return false
}

allprojects {
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            // GHSA-9pwp-9qqc-pr26, GHSA-qp49-qgx5-5m26: alle Bouncy-Castle-Module gemeinsam, sonst passen sie nicht zusammen.
            if (requested.group == "org.bouncycastle" && requested.name.endsWith("-jdk18on") && olderThan(requested.version, "1.85")) {
                useVersion("1.85")
                because("GHSA-9pwp-9qqc-pr26, GHSA-qp49-qgx5-5m26")
            }
            // GHSA-xxph-c9ww-hj94: Variante (-android, -jre) beibehalten.
            if (requested.group == "com.google.guava" && requested.name == "guava" && olderThan(requested.version, "33.7.2")) {
                val flavor = requested.version?.substringAfter('-', "jre") ?: "jre"
                useVersion("33.7.2-$flavor")
                because("GHSA-xxph-c9ww-hj94")
            }
        }
    }
}
