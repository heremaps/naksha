plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

description = gatherDescription()

kotlin {
    sourceSets {
        jvmMain {
            jvmToolchain(23)
            dependencies {
                api(project(":here-naksha-lib-base"))
                api(project(":here-naksha-lib-jbon"))
                api(project(":here-naksha-lib-mom"))
                api(project(":here-naksha-lib-model"))
                api(libs.jetbrains.annotations)

                // Can we get rid of this?
                implementation(libs.google.guava)
                implementation(libs.google.findbugs.jsr305)
                implementation(libs.commons.lang3)
                implementation(libs.google.flatbuffers)
                implementation(libs.bundles.spatial)
                implementation(libs.bundles.jackson)

                // This is required for testFixtures
                api(libs.junit.jupiter.api)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.bundles.testing)
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
