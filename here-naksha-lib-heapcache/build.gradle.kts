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
    testImplementation(Lib.mockito)
    implementation(Lib.jts_core)
}
setOverallCoverage(0.5) // only increasing allowed!