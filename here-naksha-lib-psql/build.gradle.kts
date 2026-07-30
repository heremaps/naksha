plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.js.plain.objects)
}

description = gatherDescription()

kotlin {
    jvm { }
    js { }
    sourceSets {
        commonMain {
            dependencies {
                api(project(":here-naksha-lib-base"))
                api(project(":here-naksha-lib-jbon"))
                api(project(":here-naksha-lib-geo"))
                api(project(":here-naksha-lib-model"))
            }
        }
        jvmMain {
            dependencies {
                implementation(libs.commons.lang3)
                implementation(libs.postgres)
                implementation(libs.test.containers.postgres)
                implementation(libs.commons.dbutils)
                implementation(libs.jts.core)
                implementation(libs.jts.io.common)
                implementation(libs.postgres)
                implementation(libs.caffeine)
            }
            // TODO: We should replace ${project.buildDir} with ${layout.buildDirectory}, but this is not the same:
            // println("------------ ${project.buildDir}/dist/js/productionExecutable/")
            // println("------------ ${layout.buildDirectory}/dist/js/productionExecutable/")
            resources.setSrcDirs(resources.srcDirs + "${project.rootDir}/here-naksha-lib-psql/build/dist/js/productionLibrary/")
        }
        jsMain {
            dependencies {
            }
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

                implementation(libs.slf4j.console)
                implementation(libs.test.containers.postgres)
                implementation(libs.postgres)
                implementation(libs.spatial4j)
                implementation(libs.jackson.core.databind)
            }
        }
    }
}
setOverallCoverage(0.0) // only increasing allowed!
