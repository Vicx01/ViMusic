import java.security.MessageDigest
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
}

android {
    namespace = "it.vfsfitvnm.compat"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin {
        jvmToolchain(21)
        compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    // No fake classes, substitutions, exclusions or forced transitive versions.
    implementation("com.github.MetrolistGroup.innertubex:innertubex-android:v0.11.6")
    testImplementation("junit:junit:4.13.2")
}

// Record the real artifacts without printing local cache paths or credentials.
tasks.register("writeResolvedArtifacts") {
    doLast {
        val names = listOf("debugRuntimeClasspath")
        val reportDirectory = rootProject.layout.buildDirectory.dir("reports/compat").get().asFile
        reportDirectory.mkdirs()
        for (name in names) {
            val artifacts = configurations.getByName(name).resolvedConfiguration.resolvedArtifacts
            val target = artifacts.filter {
                it.moduleVersion.id.group == "com.github.MetrolistGroup.innertubex" &&
                    it.moduleVersion.id.name == "innertubex-android" &&
                    it.moduleVersion.id.version == "v0.11.6"
            }
            check(target.isNotEmpty()) { "InnerTubeX v0.11.6 is missing from $name" }
            val lines = artifacts.map { artifact ->
                val digest = MessageDigest.getInstance("SHA-256")
                artifact.file.inputStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                    }
                }
                val sha = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
                "${artifact.moduleVersion.id} | ${artifact.file.name} | sha256=$sha"
            }.sorted()
            reportDirectory.resolve("$name-artifacts.txt").writeText(lines.joinToString("\n", postfix = "\n"))
            println("Resolved ${artifacts.size} artifacts for $name; wrote identity/hash report.")
        }
    }
}
