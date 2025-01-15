plugins {
    id("naksha.java")
    id("naksha.publish")
}

description = "Naksha Service"
dependencies {
    implementation(project(":here-naksha-lib-core"))
    implementation(project(":here-naksha-lib-psql"))
    implementation(project(":here-naksha-storage-http"))
    //implementation(project(":here-naksha-lib-extension"))
    implementation(project(":here-naksha-lib-hub"))
    implementation(project(":here-naksha-common-http"))

    implementation(Lib.log4j_slf4j)
    implementation(Lib.log4j_api)
    implementation(Lib.log4j_core)
    implementation(Lib.otel)
    implementation(Lib.commons_lang3)
    implementation(Lib.jts_core)
    implementation(Lib.postgres)
    implementation(Lib.vertx_core)
    implementation(Lib.vertx_auth_jwt)
    implementation(Lib.vertx_web)
    implementation(Lib.vertx_web_client)
    implementation(Lib.vertx_web_openapi)
    implementation(project(":here-naksha-handler-activitylog"))

    testImplementation(Lib.json_assert)
    testImplementation(Lib.resillience4j_retry)
    testImplementation(Lib.test_containers)
    testImplementation(testFixtures(project(":here-naksha-lib-core")))
    testImplementation(Lib.wiremock)
}
setOverallCoverage(0.25) // only increasing allowed!