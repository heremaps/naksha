plugins {
    id("naksha.java")
    id("naksha.publish")
}
description = "NakshaHub library"
dependencies {
    implementation(project(":here-naksha-lib-core"))
    implementation(project(":here-naksha-lib-psql"))
    implementation(project(":here-naksha-lib-handlers"))
    implementation(project(":here-naksha-lib-ext-manager"))

    implementation(Lib.commons_lang3)
    implementation(Lib.jts_core)
    implementation(Lib.postgres)
    implementation(Lib.aws_s3)

    testImplementation(Lib.json_assert)
    testImplementation(Lib.mockito)
}
setOverallCoverage(0.2) // only increasing allowed!