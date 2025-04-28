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

                implementation(libs.aws.s3)
                implementation(libs.jcl.slf4j)
                implementation(libs.cytodynamics)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.mockito)
                implementation(project(":here-naksha-lib-core"))
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
