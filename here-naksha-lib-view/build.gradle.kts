plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

description = gatherDescription()

kotlin {
    sourceSets {
        jvmMain {
            jvmToolchain(23)
            dependencies {
                api(project(":here-naksha-lib-core"))
                implementation(project(":here-naksha-lib-model"))

                implementation(libs.commons.lang3)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.bundles.testing)
                implementation(project(":here-naksha-lib-model"))
                implementation(project(":here-naksha-lib-psql"))
                implementation(libs.jts.core)
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
