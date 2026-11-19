// Start with the host toolchain used in Vivi v7.0.1. Compatibility must be tested.
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
