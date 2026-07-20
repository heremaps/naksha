plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

description = gatherDescription()

kotlin {
    jvm { }
    sourceSets {
        jvmMain {
            dependencies {
                api(project(":here-naksha-lib-jbon"))
                api(project(":here-naksha-lib-core"))
                api(project(":here-naksha-common-http"))

                implementation(libs.commons.lang3)
                implementation(libs.bundles.jackson)
            }
        }
        jvmTest {
            dependencies {
                implementation(kotlin("test-junit"))
                implementation(libs.bundles.testing)
                runtimeOnly(libs.junit.platform.launcher) // https://github.com/gradle/gradle/issues/34512

                implementation(libs.rest.assured)
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
        if (System.getenv("runConnectorIntegrationTests")?.toBoolean() != true) {
            exclude("**/integration/**")
        }
    }
}
setOverallCoverage(0.0) // only increasing allowed!
