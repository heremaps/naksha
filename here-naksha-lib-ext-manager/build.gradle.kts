plugins {
    java
    `java-library`
    `java-test-fixtures`
    `jacoco-report-aggregation`
}
description = "Naksha Extension Manager Library"
dependencies {
    api(project(":here-naksha-lib-core"))

    implementation(libs.aws.s3)
    implementation(libs.jcl.slf4j)
    implementation(libs.cytodynamics)
    testImplementation(libs.mockito)
    testImplementation(project(":here-naksha-lib-core"))
}
setOverallCoverage(0.0) // only increasing allowed!