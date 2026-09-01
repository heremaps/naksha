plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

description = gatherDescription()

kotlin {
    jvm { }
    js  { }
    sourceSets {
        commonMain {
            dependencies {
                api(project(":here-naksha-lib-base"))
                api(project(":here-naksha-lib-geo"))
            }
        }
        jvmMain {
            dependencies {
                implementation(libs.slf4j.console)
            }
            resources.setSrcDirs(resources.srcDirs + "${layout.buildDirectory}/dist/js/productionExecutable/")
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation(libs.bundles.testing)
                runtimeOnly(libs.junit.platform.launcher) // https://github.com/gradle/gradle/issues/34512
            }
        }
    }
}
setOverallCoverage(0.0) // only increasing allowed!