import java.awt.image.BufferedImage
import javax.imageio.ImageIO

plugins {
    alias(libs.plugins.agp.app)
}

// AGP's built-in Kotlin support adds stdlib even to this Java-only module.
// The entry needs only framework Activity/Intent classes, not a second runtime.
configurations.configureEach {
    // Do not alter androidLintTool: lint itself is implemented in Kotlin.
    if (name.endsWith("RuntimeClasspath") || name.endsWith("CompileClasspath")) {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    }
}

android {
    namespace = "me.weishu.kernelsu.launcher"
    compileSdk = rootProject.extra["androidCompileSdkVersion"] as Int
    buildToolsVersion = rootProject.extra["androidBuildToolsVersion"] as String
    defaultConfig {
        applicationId = "io.github.fixz.stersu.launcher"
        minSdk = 31
        targetSdk = rootProject.extra["androidTargetSdkVersion"] as Int
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// The template has no dependencies, permissions, signing key, or Manager code.
// Its placeholder is replaced with the user's cropped PNG on the device.
val generateLauncherIcon = tasks.register("generateLauncherIcon") {
    val output = layout.buildDirectory.dir("generated/launcherIcon")
    outputs.dir(output)
    doLast {
        val icon = output.get().file("drawable/entry_icon.png").asFile
        icon.parentFile.mkdirs()
        val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        ImageIO.write(image, "png", icon)
    }
}
android.sourceSets.getByName("main").res.srcDir(layout.buildDirectory.dir("generated/launcherIcon").get().asFile)
tasks.named("preBuild") { dependsOn(generateLauncherIcon) }
