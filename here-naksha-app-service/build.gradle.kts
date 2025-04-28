plugins {
    java
    `java-test-fixtures`
    `jacoco-report-aggregation`
}

description = "Naksha Service"
dependencies {
    implementation(project(":here-naksha-lib-core"))
    implementation(project(":here-naksha-lib-psql"))
    implementation(project(":here-naksha-storage-http"))
    //implementation(project(":here-naksha-lib-extension"))
    implementation(project(":here-naksha-lib-hub"))
    implementation(project(":here-naksha-common-http"))
    implementation(project(":here-naksha-lib-diff"))
    implementation(project(":here-naksha-handler-activitylog"))

    implementation(libs.commons.lang3)
    implementation(libs.otel)
    implementation(libs.postgres)
    implementation(libs.bundles.logging)
    implementation(libs.bundles.vertx)
    implementation(libs.bundles.spatial)
    implementation(libs.bundles.jackson)

    testImplementation(libs.bundles.testing)
    testImplementation(libs.resillience4j.retry)
    testImplementation(libs.test.containers)
    testImplementation(testFixtures(project(":here-naksha-lib-core")))
    testImplementation(libs.wiremock)
}
setOverallCoverage(0.25) // only increasing allowed!
