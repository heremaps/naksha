plugins {
    java
    `java-library`
    `java-test-fixtures`
    kotlin("jvm")
}

description = "Naksha Activity Log Handler"
dependencies {
    implementation(project(":here-naksha-lib-core"))
    implementation(project(":here-naksha-lib-psql"))
    implementation(project(":here-naksha-lib-handlers"))
    implementation(project(":here-naksha-lib-diff"))

    implementation(libs.flipkart.zjsonpatch)

    testImplementation(libs.bundles.testing)
    testImplementation(libs.jayway.jsonpath)
    testImplementation(testFixtures(project(":here-naksha-lib-core")))
}
setOverallCoverage(0.4) // only increasing allowed!
kotlin {
}