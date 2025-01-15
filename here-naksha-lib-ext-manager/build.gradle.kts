plugins {
    id("naksha.java")
    id("naksha.publish")
}
description = "Naksha Extension Manager Library"
dependencies {
    api(project(":here-naksha-lib-core"))

    implementation(Lib.aws_s3)
    implementation(Lib.jcl_slf4j)
    implementation(Lib.cytodynamics)
    testImplementation(Lib.mockito)
}
setOverallCoverage(0.0) // only increasing allowed!