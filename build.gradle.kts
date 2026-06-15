import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.awt.BasicStroke
import java.awt.Color
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.net.URI
import javax.imageio.ImageIO

// Build script for the TopazMD desktop app.
// Versions are pinned to a combination verified against Maven Central:
// Jewel 0.34.0 (IntelliJ Platform 253) was compiled against stable Compose 1.10.0,
// Kotlin 2.2.0 and JDK 21 — matching the Compose plugin and JBR used here.

plugins {
	kotlin("jvm") version "2.2.0"
	id("org.jetbrains.kotlin.plugin.compose") version "2.2.0"
	id("org.jetbrains.compose") version "1.10.3"
}

group = "com.bitsycore"
version = "1.0.0"

repositories {
	mavenCentral()
	google()
}

// All org.jetbrains.jewel:* modules must share the exact same version string.
val kJewelVersion = "0.34.0-253.32098.101"

dependencies {
	implementation(compose.desktop.currentOs)

	// Jewel standalone declares JNA as compile-only (the IDE normally provides it),
	// so a standalone app must add it itself, or IntUiTheme fails with NoClassDefFoundError.
	implementation("net.java.dev.jna:jna:5.14.0")

	// Standalone Jewel theme + decorated window (DecoratedWindow / TitleBar).
	implementation("org.jetbrains.jewel:jewel-int-ui-standalone:$kJewelVersion")
	implementation("org.jetbrains.jewel:jewel-int-ui-decorated-window:$kJewelVersion")

	// Markdown engine + standalone Int UI styling for the preview pane.
	implementation("org.jetbrains.jewel:jewel-markdown-core:$kJewelVersion")
	implementation("org.jetbrains.jewel:jewel-markdown-int-ui-standalone-styling:$kJewelVersion")

	// GitHub-flavored Markdown extensions (tables, alerts, strikethrough, autolinks).
	implementation("org.jetbrains.jewel:jewel-markdown-extensions-gfm-tables:$kJewelVersion")
	implementation("org.jetbrains.jewel:jewel-markdown-extensions-gfm-alerts:$kJewelVersion")
	implementation("org.jetbrains.jewel:jewel-markdown-extensions-gfm-strikethrough:$kJewelVersion")
	implementation("org.jetbrains.jewel:jewel-markdown-extensions-autolink:$kJewelVersion")

	// Syntax-highlighting engine used to color fenced code blocks in the preview.
	implementation("dev.snipme:highlights:1.0.0")
}

// Downloads the mermaid.min.js bundle into the build's resources at build time, so the running
// app can load it from the classpath and render diagrams fully offline (no runtime web fetch).
val kMermaidVersion = "11.4.1"
val downloadMermaid by tasks.registering {
	val vOut = layout.buildDirectory.file("mermaid/mermaid.min.js").get().asFile
	outputs.file(vOut)
	doLast {
		if (!vOut.exists() || vOut.length() < 1024) {
			vOut.parentFile.mkdirs()
			val vUrl = URI("https://cdn.jsdelivr.net/npm/mermaid@$kMermaidVersion/dist/mermaid.min.js").toURL()
			vUrl.openStream().use { vInput -> vOut.outputStream().use { vInput.copyTo(it) } }
		}
	}
}

sourceSets.named("main") {
	resources.srcDir(layout.buildDirectory.dir("mermaid"))
}

tasks.named("processResources") { dependsOn(downloadMermaid) }

// Downloads Google's Material Icons Outlined font (legacy "icons" family — supports CSS-style
// ligatures, so writing Text("folder") with this font family renders the folder glyph). About
// 1MB OTF; bundled into the classpath so the app renders icons offline.
val downloadMaterialIcons by tasks.registering {
	val vOut = layout.buildDirectory.file("icons-font/material-icons-outlined.otf").get().asFile
	outputs.file(vOut)
	doLast {
		if (!vOut.exists() || vOut.length() < 1024) {
			vOut.parentFile.mkdirs()
			val vUrl = URI(
				"https://raw.githubusercontent.com/google/material-design-icons/master/font/MaterialIconsOutlined-Regular.otf"
			).toURL()
			vUrl.openStream().use { vInput -> vOut.outputStream().use { vInput.copyTo(it) } }
		}
	}
}

sourceSets.named("main") {
	resources.srcDir(layout.buildDirectory.dir("icons-font"))
}

tasks.named("processResources") { dependsOn(downloadMaterialIcons) }

// Make every package* task depend on the icon generator so the PNG/ICO are written before
// jpackage looks for them. configureEach is used because Compose Desktop registers its
// package tasks lazily.
tasks.matching { it.name.startsWith("package") || it.name.startsWith("create") && it.name.contains("Distributable") }
	.configureEach { dependsOn("generateAppIcon") }

// Builds every platform-specific icon variant from the two Android-style adaptive sources
// in icons/ (ic_launcher_background.png + ic_launcher_foreground.png). The two PNGs are
// composited into a single 1024×1024 ARGB image, then re-encoded as:
//  - build/app-icon/topaz.png  (PNG)           → jpackage Linux iconFile, AppImage
//  - build/app-icon/topaz.ico  (multi-res ICO) → jpackage Windows iconFile
//  - build/app-icon/topaz.icns (multi-res ICNS)→ jpackage macOS iconFile
// All three encoders are inlined so the build script stays self-contained — no external
// tools (iconutil / png2icns / ImageMagick) needed.
val generateAppIcon by tasks.registering {
	val vBg = project.file("icons/ic_launcher_background.png")
	val vFg = project.file("icons/ic_launcher_foreground.png")
	val vPngOut = layout.buildDirectory.file("app-icon/topaz.png").get().asFile
	val vIcoOut = layout.buildDirectory.file("app-icon/topaz.ico").get().asFile
	val vIcnsOut = layout.buildDirectory.file("app-icon/topaz.icns").get().asFile
	inputs.files(vBg, vFg)
	outputs.files(vPngOut, vIcoOut, vIcnsOut)
	doLast {
		vPngOut.parentFile.mkdirs()
		generateAppIconFiles(vBg, vFg, vPngOut, vIcoOut, vIcnsOut)
	}
}

// Composites the adaptive background + foreground PNGs into a 1024×1024 master, writes the
// PNG straight out, and feeds the same in-memory bitmap to the ICO and ICNS encoders.
fun generateAppIconFiles(inBg: File, inFg: File, inPng: File, inIco: File, inIcns: File) {
	val vSize = 1024
	val vBg = ImageIO.read(inBg)
	val vFg = ImageIO.read(inFg)
	val vImage = BufferedImage(vSize, vSize, BufferedImage.TYPE_INT_ARGB)
	val vG = vImage.createGraphics()
	vG.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
	vG.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
	vG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
	vG.drawImage(vBg, 0, 0, vSize, vSize, null)
	vG.drawImage(vFg, 0, 0, vSize, vSize, null)
	vG.dispose()
	ImageIO.write(vImage, "PNG", inPng)
	writeIco(inIco, vImage)
	writeIcns(inIcns, vImage)
}

// Rescales the source bitmap to inSize × inSize using high-quality interpolation. Shared by
// the ICO and ICNS encoders for the per-entry payloads.
fun scalePng(inSource: BufferedImage, inSize: Int): ByteArray {
	val vScaled = BufferedImage(inSize, inSize, BufferedImage.TYPE_INT_ARGB)
	val vG = vScaled.createGraphics()
	vG.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
	vG.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
	vG.drawImage(inSource, 0, 0, inSize, inSize, null)
	vG.dispose()
	val vBaos = ByteArrayOutputStream()
	ImageIO.write(vScaled, "PNG", vBaos)
	return vBaos.toByteArray()
}

// Writes a multi-resolution ICO. ICO is a thin container of PNG payloads — we ship 256/128/
// 64/48/32/16 so Windows can pick the right size for taskbar / explorer / start menu.
fun writeIco(inFile: File, inSource: BufferedImage) {
	val vSizes = intArrayOf(256, 128, 64, 48, 32, 16)
	val vPngs = vSizes.map { it to scalePng(inSource, it) }

	DataOutputStream(inFile.outputStream()).use { vOut ->
		// ICONDIR header.
		vOut.writeShortLE(0)              // reserved
		vOut.writeShortLE(1)              // type = ICO
		vOut.writeShortLE(vSizes.size)

		// ICONDIRENTRYs.
		var vOffset = 6 + 16 * vSizes.size
		for ((vSize, vBytes) in vPngs) {
			vOut.writeByte(if (vSize >= 256) 0 else vSize) // width  (0 = 256)
			vOut.writeByte(if (vSize >= 256) 0 else vSize) // height (0 = 256)
			vOut.writeByte(0)             // palette colors (0 = none)
			vOut.writeByte(0)             // reserved
			vOut.writeShortLE(1)          // planes
			vOut.writeShortLE(32)         // bpp
			vOut.writeIntLE(vBytes.size)
			vOut.writeIntLE(vOffset)
			vOffset += vBytes.size
		}

		// Image payloads (PNG-in-ICO).
		for ((_, vBytes) in vPngs) vOut.write(vBytes)
	}
}

// Writes a multi-resolution ICNS — Apple's icon container, equivalent of ICO. Each entry is
// a 4-byte type tag + big-endian 32-bit length + payload (PNG for modern macOS). We include
// ic07/08/09/10 plus the @2x retina aliases (ic13/14) so the Finder, Dock, About panel and
// alert dialogs all get a sharp asset. Format reference: Apple ICNS file format.
fun writeIcns(inFile: File, inSource: BufferedImage) {
	data class Entry(val type: String, val size: Int)
	val vEntries =
		listOf(
			Entry("ic07", 128),    // 128x128
			Entry("ic08", 256),    // 256x256
			Entry("ic09", 512),    // 512x512
			Entry("ic10", 1024),   // 1024x1024 (also serves as 512x512@2x)
			Entry("ic11", 32),     // 16x16@2x
			Entry("ic12", 64),     // 32x32@2x
			Entry("ic13", 256),    // 128x128@2x — same payload size as ic08
			Entry("ic14", 512),    // 256x256@2x — same payload size as ic09
		)
	val vPayloads = vEntries.map { it to scalePng(inSource, it.size) }
	val vTotalSize = 8 + vPayloads.sumOf { 8 + it.second.size }

	DataOutputStream(inFile.outputStream()).use { vOut ->
		vOut.writeBytes("icns")
		vOut.writeInt(vTotalSize)        // BE — DataOutputStream's default
		for ((vEntry, vBytes) in vPayloads) {
			vOut.writeBytes(vEntry.type)
			vOut.writeInt(8 + vBytes.size)
			vOut.write(vBytes)
		}
	}
}

// DataOutputStream writes big-endian by default; ICO is little-endian for shorts/ints.
fun DataOutputStream.writeShortLE(inValue: Int) {
	writeByte(inValue and 0xFF)
	writeByte((inValue shr 8) and 0xFF)
}
fun DataOutputStream.writeIntLE(inValue: Int) {
	writeByte(inValue and 0xFF)
	writeByte((inValue shr 8) and 0xFF)
	writeByte((inValue shr 16) and 0xFF)
	writeByte((inValue shr 24) and 0xFF)
}

// Pin the toolchain to the JetBrains Runtime so Kotlin compilation and the Compose
// `run` task both use JBR (required by DecoratedWindow). foojay downloads it if absent.
java {
	toolchain {
		languageVersion.set(JavaLanguageVersion.of(21))
		vendor.set(JvmVendorSpec.JETBRAINS)
	}
}

kotlin {
	compilerOptions {
		jvmTarget = JvmTarget.JVM_21
		// Jewel's Markdown and theming APIs are gated behind an opt-in annotation.
		optIn.addAll(
			"org.jetbrains.jewel.foundation.ExperimentalJewelApi",
			"androidx.compose.foundation.ExperimentalFoundationApi",
		)
	}
}

// Resolve the JBR provisioned by the toolchain so the Compose `run` task can target it.
val jbrLauncher =
	javaToolchains.launcherFor {
		languageVersion.set(JavaLanguageVersion.of(21))
		vendor.set(JvmVendorSpec.JETBRAINS)
	}

compose.desktop {
	application {
		mainClass = "com.bitsycore.topazmd.MainKt"
		// The `run` task uses the Gradle daemon JVM unless javaHome is set; the daemon
		// may be a non-JBR JDK, which DecoratedWindow rejects. Force it onto the JBR.
		javaHome = jbrLauncher.get().metadata.installationPath.asFile.absolutePath
		// JNA (Jewel's standalone dep) reflectively reads sun.misc.Unsafe.theUnsafe — that
		// class lives in the `jdk.unsupported` module, not `java.base`. The other --add-opens
		// loosen JDK internals Jewel and the JBR reach into reflectively. Baked into the
		// packaged app's launcher args so the .app / installed binary doesn't fail at startup.
		jvmArgs += listOf(
			"--add-opens", "jdk.unsupported/sun.misc=ALL-UNNAMED",
			"--add-opens", "java.base/java.lang=ALL-UNNAMED",
			"--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
			"--add-opens", "java.base/java.nio=ALL-UNNAMED",
		)
		// ProGuard is on by default for release builds in Compose Desktop, but Jewel, JNA and
		// JBR rely on extensive reflection that would need hundreds of keep rules to shrink
		// safely. Disabling it makes packageRelease* just bundle the JAR + JBR — larger but
		// reliable, and that's what we ship in CI.
		buildTypes.release.proguard {
			isEnabled.set(false)
		}
		nativeDistributions {
			targetFormats(TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.Dmg)
			packageName = "TopazMD"
			packageVersion = "1.0.0"
			description = "Compose for Desktop Markdown editor with Jewel UI."
			vendor = "Bitsy"
			licenseFile.set(project.file("LICENSE"))
			// jpackage builds a minimal runtime image containing only the modules jlink can
			// detect from our compiled classpath. JNA's reflective sun.misc.Unsafe access
			// looks like nothing to jlink, so jdk.unsupported gets stripped and the .app
			// dies at startup with "sun/misc/Unsafe". Force it in here.
			modules("jdk.unsupported")
			// Generated app icons under build/app-icon/* — produced by generateAppIcon from
			// icons/ic_launcher_{background,foreground}.png. Every platform gets its native
			// format so the installer / .app / .deb / .rpm / .msi all show the topaz icon.
			val vIconPng = layout.buildDirectory.file("app-icon/topaz.png").get().asFile
			val vIconIco = layout.buildDirectory.file("app-icon/topaz.ico").get().asFile
			val vIconIcns = layout.buildDirectory.file("app-icon/topaz.icns").get().asFile
			linux { iconFile.set(vIconPng) }
			windows { iconFile.set(vIconIco) }
			macOS { iconFile.set(vIconIcns) }
		}
	}
}
