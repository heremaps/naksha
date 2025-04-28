plugins {
    java
    `java-library`
    `java-test-fixtures`
    `jacoco-report-aggregation`
}

description = "Naksha Http Storage Module"
java {
    withJavadocJar()
    withSourcesJar()
}
dependencies {
    implementation(project(":here-naksha-lib-jbon"))
    implementation(project(":here-naksha-lib-core"))
    implementation(project(":here-naksha-common-http"))

    implementation(libs.commons.lang3)
    implementation(libs.bundles.jackson)

    testImplementation(libs.bundles.testing)
}
setOverallCoverage(0.0) // only increasing allowed!
