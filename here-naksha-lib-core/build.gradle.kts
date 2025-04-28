plugins {
    java
    `java-library`
    `java-test-fixtures`
    jacoco
    kotlin("jvm")
}

description = "Naksha Core Library"

java {
    withJavadocJar()
    withSourcesJar()
}
dependencies {
    api(project(":here-naksha-lib-base"))
    api(project(":here-naksha-lib-jbon"))
    api(project(":here-naksha-lib-model"))
    api(libs.jetbrains.annotations)

    // Can we get rid of this?
    implementation(libs.google.guava)
    implementation(libs.google.findbugs.jsr305)
    implementation(libs.commons.lang3)
    implementation(libs.google.flatbuffers)
    implementation(libs.bundles.spatial)
    implementation(libs.bundles.jackson)

    // This is required for testFixtures
    api(libs.junit.jupiter.api)

    testImplementation(libs.bundles.testing)
}
setOverallCoverage(0.0) // only increasing allowed!

kotlin {
}

tasks {
    // Suppress Javadoc errors (we document our checked exceptions).
    javadoc {
        options {
            this as StandardJavadocDocletOptions
            addBooleanOption("Xdoclint:none", true)
            addStringOption("Xmaxwarns", "1")
        }
    }
}