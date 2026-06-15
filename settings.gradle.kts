// Project settings: repositories for plugins/dependencies, the foojay toolchain
// resolver (so Gradle can download the JetBrains Runtime for the toolchain), and the
// root project name. Block order is significant: pluginManagement, then plugins, then
// dependencyResolutionManagement.

pluginManagement {
	repositories {
		gradlePluginPortal()
		mavenCentral()
		google()
	}
}

plugins {
	// Resolves/provisions JDKs (including the JetBrains Runtime) for Java toolchains
	// via the foojay disco API — makes the JBR requirement portable, no hard-coded path.
	id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
	repositories {
		mavenCentral()
		google()
	}
}

rootProject.name = "TopazMD"
