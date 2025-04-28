plugins {
    java
    `java-library`
    `java-test-fixtures`
    `jacoco-report-aggregation`
}

description = "Naksha Handlers library"
dependencies {
    implementation(project(":here-naksha-lib-core"))
    implementation(project(":here-naksha-lib-model"))
    implementation(project(":here-naksha-lib-view"))
    implementation(project(":here-naksha-storage-http"))

    implementation(libs.commons.lang3)
    implementation(libs.commons.dbutils)
    implementation(libs.bundles.jackson)

    testImplementation(libs.bundles.testing)
    testImplementation(testFixtures(project(":here-naksha-lib-core")))
}
setOverallCoverage(0.0)