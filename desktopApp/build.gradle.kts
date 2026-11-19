import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.jvm.tasks.Jar

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val isUniversalUberJar = providers.gradleProperty("universalUberJar")
    .map { it.toBoolean() }
    .getOrElse(false)

val rawVersion = providers.gradleProperty("packageVersion")
    .map { it.removePrefix("v") }
    .orElse("1.0.0")
    .get()

val nativePackageVersion = rawVersion
    .substringBefore("-")
    .let { ver ->
        val parts = ver.split(".")
        if (parts.size == 3 && parts.all { it.toIntOrNull() != null }) {
            val major = parts[0].toInt()
            if (major >= 1) ver else "1.${parts[1]}.${parts[2]}"
        } else {
            "1.0.0"
        }
    }

dependencies {
    implementation(project(":shared"))

    if (isUniversalUberJar) {
        implementation(libs.compose.desktop.linux.x64)
        implementation(libs.compose.desktop.linux.arm64)
        implementation(libs.compose.desktop.windows.x64)
        implementation(libs.compose.desktop.windows.arm64)
        implementation(libs.compose.desktop.macos.x64)
        implementation(libs.compose.desktop.macos.arm64)
    } else {
        implementation(compose.desktop.currentOs)
    }
    implementation(libs.compose.components.resources)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "me.danikvitek.lab1.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.AppImage)
            packageName = "me.danikvitek.lab1"
            packageVersion = nativePackageVersion
            modules("java.compiler", "java.instrument", "java.sql", "jdk.unsupported")
        }
    }
}

tasks.withType<Jar>().configureEach {
    if (name == "packageUberJarForCurrentOS" && isUniversalUberJar) {
        archiveFileName.set("me.danikvitek.lab1-universal-$rawVersion.jar")
    }
}
