plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

description = gatherDescription()

kotlin {
    jvm {}
    sourceSets {
        jvmMain {
            jvmToolchain(11)
            dependencies {
                implementation(project(":here-naksha-lib-model"))
                implementation(project(":here-naksha-lib-handlers"))
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
