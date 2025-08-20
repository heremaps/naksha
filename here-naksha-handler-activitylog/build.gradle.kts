plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

description = gatherDescription()

kotlin {
    jvm {}
    sourceSets {
        jvmMain {
            jvmToolchain(23)
            dependencies {
                implementation(project(":here-naksha-lib-core"))
                implementation(project(":here-naksha-lib-psql"))
                implementation(project(":here-naksha-lib-handlers"))
                implementation(project(":here-naksha-lib-diff"))

                implementation(libs.flipkart.zjsonpatch)
            }
        }
        jvmTest {
            dependencies {
                implementation(project(":here-naksha-lib-core"))
                implementation(libs.bundles.testing)
                implementation(libs.jayway.jsonpath)
            }
        }
    }
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
