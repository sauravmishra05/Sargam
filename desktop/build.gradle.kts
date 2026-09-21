import org.gradle.api.attributes.java.TargetJvmVersion
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.awt.Color
import java.awt.image.BufferedImage
import java.util.zip.ZipFile
import javax.imageio.ImageIO

plugins {
    kotlin("jvm")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    id("org.jetbrains.compose") version "1.7.3"
}

kotlin {
    jvmToolchain(21)
}

compose.desktop {
    application {
        mainClass = "com.metrolist.desktop.MainKt"
        jvmArgs("-Dskiko.renderApi=DIRECT3D,SOFTWARE", "-Dcompose.swing.render.on.graphics=true", "-Dfile.encoding=UTF-8")
        nativeDistributions {
            targetFormats(TargetFormat.AppImage, TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Sargam"
            packageVersion = "13.6.15"
            description = "Sargam Desktop Music Player"
            copyright = "© 2026 Sargam"
            vendor = "Sargam"

            windows {
                menuGroup = "Sargam"
                upgradeUuid = "6f2e8312-3a9d-4c81-8b17-74291ad9431e"
            }
        }
    }
}

val packageMsix = tasks.register("packageMsix") {
    group = "distribution"
    description = "Packages Sargam Desktop application into a Microsoft Store compatible x64 MSIX package using Windows SDK MakeAppx.exe"
    dependsOn("packageAppImage")
    notCompatibleWithConfigurationCache("Custom MSIX packaging task")

    doLast {
        val buildDir = layout.buildDirectory.get().asFile
        val msixDir = File(buildDir, "compose/binaries/main/msix").apply { mkdirs() }
        val stagedDir = File(buildDir, "compose/tmp/packageMsix/staged").apply {
            deleteRecursively()
            mkdirs()
        }

        val appImageDir = File(buildDir, "compose/binaries/main/app/Sargam")
        require(appImageDir.exists()) { "AppImage directory not found at ${appImageDir.absolutePath}" }

        // Copy complete native Sargam app image (Sargam.exe, app/Sargam.cfg, app/*.jar, runtime/)
        appImageDir.copyRecursively(stagedDir, overwrite = true)

        val assetsDir = File(stagedDir, "Assets").apply { mkdirs() }
        val sourceLogo = rootProject.file("assets/ic_launcher-playstore.png")
        if (sourceLogo.exists()) {
            val originalImage = ImageIO.read(sourceLogo)
            fun scaleAndSave(width: Int, height: Int, name: String) {
                val scaled = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
                val g = scaled.createGraphics()
                g.drawImage(originalImage, 0, 0, width, height, null)
                g.dispose()
                ImageIO.write(scaled, "png", File(assetsDir, name))
            }
            scaleAndSave(150, 150, "Square150x150Logo.png")
            scaleAndSave(44, 44, "Square44x44Logo.png")
            scaleAndSave(50, 50, "StoreLogo.png")
            scaleAndSave(310, 150, "Wide310x150Logo.png")
            scaleAndSave(620, 300, "SplashScreen.png")
            logger.lifecycle("Generated store assets from existing store logo: ${sourceLogo.absolutePath}")
        } else {
            fun makeLogo(width: Int, height: Int, name: String) {
                val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
                val g = img.createGraphics()
                g.color = Color(0xFF, 0x52, 0x52)
                g.fillRect(0, 0, width, height)
                g.dispose()
                ImageIO.write(img, "png", File(assetsDir, name))
            }
            makeLogo(150, 150, "Square150x150Logo.png")
            makeLogo(44, 44, "Square44x44Logo.png")
            makeLogo(50, 50, "StoreLogo.png")
            makeLogo(310, 150, "Wide310x150Logo.png")
            makeLogo(620, 300, "SplashScreen.png")
        }

        val manifestContent = """
            <?xml version="1.0" encoding="utf-8"?>
            <Package xmlns="http://schemas.microsoft.com/appx/manifest/foundation/windows10"
                     xmlns:uap="http://schemas.microsoft.com/appx/manifest/uap/windows10"
                     xmlns:uap10="http://schemas.microsoft.com/appx/manifest/uap/windows10/10"
                     xmlns:desktop="http://schemas.microsoft.com/appx/manifest/desktop/windows10"
                     xmlns:rescap="http://schemas.microsoft.com/appx/manifest/foundation/windows10/restrictedcapabilities"
                     IgnorableNamespaces="uap uap10 desktop rescap">
              <Identity Name="53055Sargam.Sargam"
                        Publisher="CN=2889F95D-033D-4EB6-8E3A-D64952FA7A90"
                        Version="13.6.15.0"
                        ProcessorArchitecture="x64" />
              <Properties>
                <DisplayName>Sargam</DisplayName>
                <PublisherDisplayName>Sargam</PublisherDisplayName>
                <Logo>Assets/StoreLogo.png</Logo>
                <Description>Sargam Desktop Music Player</Description>
              </Properties>
              <Resources>
                <Resource Language="en-us" />
              </Resources>
              <Dependencies>
                <TargetDeviceFamily Name="Windows.Desktop" MinVersion="10.0.17763.0" MaxVersionTested="10.0.26100.0" />
              </Dependencies>
              <Capabilities>
                <Capability Name="internetClient" />
                <rescap:Capability Name="runFullTrust" />
              </Capabilities>
              <Applications>
                <Application Id="App"
                             Executable="Sargam.exe"
                             EntryPoint="Windows.FullTrustApplication"
                             uap10:TrustLevel="mediumIL"
                             uap10:RuntimeBehavior="packagedClassicApp">
                  <uap:VisualElements DisplayName="Sargam"
                                      Description="Sargam Desktop Music Player"
                                      BackgroundColor="transparent"
                                      Square150x150Logo="Assets/Square150x150Logo.png"
                                      Square44x44Logo="Assets/Square44x44Logo.png">
                    <uap:DefaultTile Wide310x150Logo="Assets/Wide310x150Logo.png" />
                    <uap:SplashScreen Image="Assets/SplashScreen.png" />
                  </uap:VisualElements>
                </Application>
              </Applications>
            </Package>
        """.trimIndent()

        File(stagedDir, "AppxManifest.xml").writeText(manifestContent)

        val msixFile = File(msixDir, "Sargam-13.6.15.msix")
        if (msixFile.exists()) msixFile.delete()

        val makeAppxExe = File("C:/Program Files (x86)/Windows Kits/10/bin/10.0.28000.0/x64/makeappx.exe")
        require(makeAppxExe.exists()) { "MakeAppx.exe not found at ${makeAppxExe.absolutePath}" }

        val logFile = File(buildDir, "tmp/makeappx.log").apply { parentFile.mkdirs() }
        logger.lifecycle("Running Windows SDK MakeAppx.exe pack...")
        val process = ProcessBuilder(
            makeAppxExe.absolutePath,
            "pack",
            "/h", "sha256",
            "/d", stagedDir.absolutePath,
            "/p", msixFile.absolutePath,
            "/o"
        ).redirectOutput(logFile)
         .redirectError(logFile)
         .start()

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            val logText = if (logFile.exists()) logFile.readText() else "No log"
            throw GradleException("MakeAppx.exe failed with exit code $exitCode:\n$logText")
        }

        logger.lifecycle("Official Windows SDK MakeAppx MSIX Package generated successfully at: ${msixFile.absolutePath}")
    }
}

tasks.withType<Copy>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType<Tar>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType<Zip>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

val buildTypeAttr = Attribute.of("com.android.build.api.attributes.BuildTypeAttr", String::class.java)
val artifactTypeAttr = Attribute.of("artifactType", String::class.java)

val timberAar = configurations.detachedConfiguration(dependencies.create(libs.timber.get().toString()))
val timberJarFile = file("$projectDir/libs/timber-5.0.1.jar")

if (!timberJarFile.exists()) {
    try {
        timberJarFile.parentFile.mkdirs()
        timberAar.files.firstOrNull { it.name.endsWith(".aar") }?.let { aarFile ->
            copy {
                from(zipTree(aarFile).matching { include("classes.jar") })
                into(timberJarFile.parentFile)
                rename("classes.jar", "timber-5.0.1.jar")
            }
        }
    } catch (e: Exception) {
        // Handled during resolution
    }
}

listOf("compileClasspath", "runtimeClasspath", "testCompileClasspath", "testRuntimeClasspath").forEach { configName ->
    configurations.findByName(configName)?.attributes {
        attribute(buildTypeAttr, "release")
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    if (timberJarFile.exists()) {
        implementation(files(timberJarFile))
    } else {
        implementation(libs.timber)
    }
    implementation(project(":innertube")) {
        attributes {
            attribute(artifactTypeAttr, "android-classes-jar")
        }
        exclude(group = "com.jakewharton.timber", module = "timber")
    }
    implementation(project(":lrclib")) {
        attributes {
            attribute(artifactTypeAttr, "android-classes-jar")
        }
        exclude(group = "com.jakewharton.timber", module = "timber")
    }
    implementation(project(":kugou")) {
        attributes {
            attribute(artifactTypeAttr, "android-classes-jar")
        }
        exclude(group = "com.jakewharton.timber", module = "timber")
    }
    implementation(project(":betterlyrics")) {
        attributes {
            attribute(artifactTypeAttr, "android-classes-jar")
        }
        exclude(group = "com.jakewharton.timber", module = "timber")
    }
    // implementation(project(":spotify"))

    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.ui)
    implementation(compose.material3)
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.skiko:skiko-awt-runtime-windows-x64:0.9.22.2")

    implementation(libs.coil)
    implementation(libs.coil.network.okhttp)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)

    val osName = System.getProperty("os.name").lowercase()
    val platform = when {
        osName.contains("win") -> "win"
        osName.contains("mac") -> "mac"
        else -> "linux"
    }
    implementation("org.openjfx:javafx-base:21.0.2:$platform")
    implementation("org.openjfx:javafx-graphics:21.0.2:$platform")
    implementation("org.openjfx:javafx-media:21.0.2:$platform")
    implementation("org.openjfx:javafx-swing:21.0.2:$platform")
    implementation("org.openjfx:javafx-web:21.0.2:$platform")

    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")
}
