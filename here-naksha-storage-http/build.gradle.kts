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
                implementation(project(":here-naksha-lib-jbon"))
                implementation(project(":here-naksha-lib-core"))
                implementation(project(":here-naksha-common-http"))

                implementation(libs.commons.lang3)
                implementation(libs.bundles.jackson)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.bundles.testing)
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
