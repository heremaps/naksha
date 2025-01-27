description = "Naksha Core Library"

plugins {
    id("naksha.java")
    id("naksha.publish")
    kotlin("jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    withJavadocJar()
    withSourcesJar()
}
dependencies {
    // Can we get rid of this?
    implementation(Lib.google_guava)
    implementation(Lib.commons_lang3)
    implementation(Lib.jts_core)
    implementation(Lib.jts_io_common)
    implementation(Lib.google_flatbuffers)
    api(project(":here-naksha-lib-base"))
    api(project(":here-naksha-lib-jbon"))
    api(project(":here-naksha-lib-model"))
    implementation(Lib.spatial4j)
    testImplementation(Lib.mockito)
    testImplementation(Lib.json_assert)
}
setOverallCoverage(0.0) // only increasing allowed!
kotlin {
    jvmToolchain(11)
}