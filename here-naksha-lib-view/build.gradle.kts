plugins {
    java
    `java-library`
    `java-test-fixtures`
    `jacoco-report-aggregation`
}

description = "Naksha View Library"

java {
    withJavadocJar()
    withSourcesJar()
}
dependencies {
    api(project(":here-naksha-lib-core"))
    implementation(project(":here-naksha-lib-model"))

    implementation(libs.commons.lang3)
    testImplementation(libs.mockito)
    testImplementation(project(":here-naksha-lib-model"))
    testImplementation(project(":here-naksha-lib-psql"))
    testImplementation(libs.jts.core)
}
setOverallCoverage(0.0) // only increasing allowed!

tasks {
    // Suppress Javadoc errors (we document our checked exceptions).
    javadoc {
        options {
            this as StandardJavadocDocletOptions
            addBooleanOption("Xdoclint:none", true)
            addStringOption("Xmaxwarns", "1")
        }
    }
}