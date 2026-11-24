buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }

    dependencies {
        classpath("com.android.tools.build", "gradle", "9.1.1")
        classpath(kotlin("gradle-plugin", libs.versions.kotlin.get()))
        classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:${libs.versions.kotlin.get()}")
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}

subprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            if (project.findProperty("enableComposeCompilerReports") == "true") {
                arrayOf("reports", "metrics").forEach {
                    freeCompilerArgs.addAll(listOf(
                        "-P", "plugin:androidx.compose.compiler.plugins.kotlin:${it}Destination=${project.layout.buildDirectory.get().asFile.absolutePath}/compose_metrics"
                    ))
                }
            }
        }
    }
}
