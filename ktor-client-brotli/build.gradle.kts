plugins {
    kotlin("jvm")
}

sourceSets.all {
    java.srcDir("src/$name/kotlin")
}

dependencies {
    implementation(libs.ktor.client.encoding)
    implementation(libs.brotli)

    testImplementation(testLibs.junit)
    testImplementation(libs.ktor.client.mock)
}