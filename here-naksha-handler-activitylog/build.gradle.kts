plugins {
    id("naksha.java")
    id("naksha.publish")
}
description = "Naksha Activity Log Handler"
dependencies {
    implementation(project(":here-naksha-lib-core"))
    implementation(project(":here-naksha-lib-psql"))
    implementation(project(":here-naksha-lib-handlers"))
    implementation(project(":here-naksha-lib-diff"))

    implementation(Lib.flipkart_zjsonpatch)
    testImplementation(Lib.jayway_jsonpath)
    testImplementation(Lib.mockito)
    testImplementation(Lib.json_assert)
    testImplementation(testFixtures(project(":here-naksha-lib-core")))
}
setOverallCoverage(0.4) // only increasing allowed!