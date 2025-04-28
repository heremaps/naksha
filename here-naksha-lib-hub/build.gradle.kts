plugins {
    java
    `java-library`
    `java-test-fixtures`
    `jacoco-report-aggregation`
}

description = "NakshaHub library"
dependencies {
    implementation(project(":here-naksha-lib-core"))
    implementation(project(":here-naksha-lib-model"))
    implementation(project(":here-naksha-lib-psql"))
    implementation(project(":here-naksha-lib-handlers"))
    implementation(project(":here-naksha-lib-ext-manager"))

    implementation(libs.commons.lang3)
    implementation(libs.jts.core)
    implementation(libs.postgres)
    implementation(libs.aws.s3)

    implementation(libs.bundles.jackson)

    testImplementation(libs.json.assert)
    testImplementation(libs.mockito)
}
setOverallCoverage(0.2) // only increasing allowed!