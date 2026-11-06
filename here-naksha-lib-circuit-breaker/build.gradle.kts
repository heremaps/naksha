plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

description = gatherDescription()

kotlin {
    jvm { }
    sourceSets {
        jvmMain {
            dependencies {
                api(project(":here-naksha-lib-model"))
                api(libs.resilience4j.circuitbreaker)
                implementation(libs.slf4j.api)
                implementation(libs.jetbrains.annotations)
            }
        }
        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation(libs.bundles.testing)
                runtimeOnly(libs.junit.platform.launcher)
            }
        }
    }
}

tasks {
    getByName<Jar>("jvmJar") { dependsOn("jvmProcessResources") }
    getByName<ProcessResources>("jvmTestProcessResources") { dependsOn("jvmProcessResources") }
    getByName<Test>("jvmTest") { useJUnitPlatform() }
}
setOverallCoverage(0.0)
