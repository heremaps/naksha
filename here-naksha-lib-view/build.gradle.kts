description = "Naksha View Library"

plugins {
    id("naksha.java")
    id("naksha.publish")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    withJavadocJar()
    withSourcesJar()
}
dependencies {
    api(project(":here-naksha-lib-core"))
    implementation(project(":here-naksha-lib-model"))

    implementation(Lib.commons_lang3)
    testImplementation(Lib.mockito)
    testImplementation(project(":here-naksha-lib-model"))
    testImplementation(Lib.jts_core)
}
setOverallCoverage(0.0) // only increasing allowed!