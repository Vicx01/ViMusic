// Isolated compatibility experiment. Deliberately not included in ViMusic's settings.
// Mirrors Vivi v7.0.1's buildscript rather than claiming this works with ViMusic's toolchain.
buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:9.1.1")
        classpath(kotlin("gradle-plugin", "2.3.10"))
    }
}
