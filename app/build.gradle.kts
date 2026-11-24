plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.android.legacy-kapt") version "9.1.1"
}

android {
    compileSdk = 37

    defaultConfig {
        applicationId = "it.vfsfitvnm.vimusic"
        // InnerTubeX migration baseline: Android 8.0 and later.
        minSdk = 26
        targetSdk = 33
        versionCode = 20
        versionName = "0.5.4"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    splits {
        abi {
            reset()
            isUniversalApk = true
        }
    }

    namespace = "it.vfsfitvnm.vimusic"

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            manifestPlaceholders["appName"] = "Debug"
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            manifestPlaceholders["appName"] = "ViMusic"
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    sourceSets.all {
        kotlin.directories.add("src/$name/kotlin")
    }
    // Historical Room schema exports are used only by instrumentation tests.
    sourceSets.getByName("androidTest").assets.directories.add("$projectDir/schemas")

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

}

kapt {
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

dependencies {
    implementation(projects.composePersist)
    implementation(projects.composeRouting)
    implementation(projects.composeReordering)

    implementation(libs.compose.activity)
    implementation(libs.compose.foundation)
    implementation("androidx.compose.foundation:foundation-layout:1.3.0-rc01")
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.util)
    implementation(libs.compose.ripple)
    implementation(libs.compose.shimmer)
    implementation(libs.compose.coil)

    implementation(libs.palette)

    implementation(libs.exoplayer)

    implementation(libs.room)
    kapt(libs.room.compiler)

    // The legacy provider remains active until InnerTubeX playback is validated.
    implementation(projects.innertube)
    implementation(projects.kugou)
    implementation("com.github.MetrolistGroup.innertubex:innertubex-android:v0.11.6")

    testImplementation(testLibs.junit)
    testImplementation(libs.ktor.client.mock)

    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.room:room-testing:${libs.versions.room.get()}")

    coreLibraryDesugaring(libs.desugaring)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-receivers")
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
    }
}
