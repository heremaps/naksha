plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

description = gatherDescription()

kotlin {
    jvm {
    }
    sourceSets {
        jvmMain {
            dependencies {
                api(project(":here-naksha-lib-core"))
                api(project(":here-naksha-lib-model"))
                api(project(":here-naksha-lib-psql"))
                api(project(":here-naksha-lib-handlers"))
                api(project(":here-naksha-lib-ext-manager"))

                implementation(libs.commons.lang3)
                implementation(libs.jts.core)
                implementation(libs.postgres)
                implementation(libs.aws.s3)
                implementation(libs.bundles.jackson)
            }
        }
        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation(libs.bundles.testing)
                runtimeOnly(libs.junit.platform.launcher) // https://github.com/gradle/gradle/issues/34512

                implementation(libs.json.assert)
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
