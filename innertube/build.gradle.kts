
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
        exclude(group = "org.mozilla", module = "rhino")
    }
    // NewPipe's Rhino 1.8.1 dependency cannot be dexed by this project's older Android D8.
    implementation("org.mozilla:rhino:1.7.15")

    testImplementation(testLibs.junit)
}
