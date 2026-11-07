
plugins {
    kotlin("jvm")
    @Suppress("DSL_SCOPE_VIOLATION")
    alias(libs.plugins.kotlin.serialization)
}

sourceSets.all {
    java.srcDir("src/$name/kotlin")
}

tasks.withType<Test>().configureEach {
    if (project.hasProperty("liveNewPipeProbe")) {
        systemProperty("liveNewPipeProbe", project.property("liveNewPipeProbe").toString())
    }
}

dependencies {
    implementation(projects.ktorClientBrotli)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.encoding)
    implementation(libs.ktor.client.serialization)
    implementation(libs.ktor.serialization.json)
    implementation(libs.newpipe.extractor) {
        exclude(group = "com.github.spotbugs", module = "spotbugs-annotations")
    }

    testImplementation(testLibs.junit)
}
