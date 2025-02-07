plugins {
    id("naksha.java")
    id("naksha.publish")
}

description = "Naksha Handlers library"
dependencies {
    implementation(project(":here-naksha-lib-core"))
    implementation(project(":here-naksha-lib-model"))
    implementation(project(":here-naksha-lib-view"))
    implementation(project(":here-naksha-storage-http"))

    implementation(Lib.commons_lang3)
    implementation(Lib.commons_dbutils)

    testImplementation(Lib.mockito)
    testImplementation(Lib.json_assert)
    testImplementation(testFixtures(project(":here-naksha-lib-core")))
}
    setOverallCoverage(0.0)