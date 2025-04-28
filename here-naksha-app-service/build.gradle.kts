plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

description = gatherDescription()

kotlin {
    sourceSets {
        jvmMain {
            jvmToolchain(23)
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
            }
        }
        jvmTest {
            dependencies {
                implementation(project(":here-naksha-lib-core"))
                implementation(libs.bundles.testing)
                implementation(libs.resillience4j.retry)
                implementation(libs.test.containers)
                //implementation(testFixtures(project(":here-naksha-lib-core")))
                implementation(libs.wiremock)
            }
        }
    }

    jvm {}
}

tasks {
    getByName<Jar>("jvmJar") { dependsOn("jvmProcessResources") }
    getByName<ProcessResources>("jvmTestProcessResources") { dependsOn("jvmProcessResources") }
    getByName<Test>("jvmTest") {
        useJUnitPlatform()
        maxHeapSize = "6g"
    }
}
setOverallCoverage(0.0) // only increasing allowed!

