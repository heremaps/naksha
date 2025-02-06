plugins {
    id("naksha.java")
    id("naksha.publish")
}
description = "Naksha Http Storage Module"
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withJavadocJar()
    withSourcesJar()
}
dependencies {
    implementation(project(":here-naksha-lib-core"))
    implementation(project(":here-naksha-common-http"))

    implementation(Lib.commons_lang3)

    testImplementation(Lib.mockito)
}
setOverallCoverage(0.0) // only increasing allowed!
