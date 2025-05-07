plugins {
    id("naksha.java")
    id("naksha.publish")
}
description = "Naksha Heap Caching Library"
java {
    withJavadocJar()
    withSourcesJar()
}
dependencies {
    api(project(":here-naksha-lib-core"))
    testImplementation(libs.mockito)
    implementation(libs.jts.core)
}
setOverallCoverage(0.5) // only increasing allowed!