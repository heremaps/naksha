plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

description = gatherDescription()

kotlin {
    jvm { }
    js { }
    sourceSets {
        commonMain {
            dependencies {
                implementation(kotlin("stdlib"))
                implementation(libs.kotlinx.datetime)
                implementation(project(":here-naksha-lib-base"))
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation(libs.kotlinx.datetime)
            }
        }
        jvmTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlintest.runner.junit5)
                runtimeOnly(libs.junit.jupiter.engine)
                implementation(libs.junit.jupiter.api)
                implementation(libs.junit.params)
                api(libs.slf4j.api)
                implementation(libs.slf4j.console)
                implementation("org.skyscreamer:jsonassert:1.5.1")
                runtimeOnly(libs.junit.platform.launcher) // https://github.com/gradle/gradle/issues/34512
            }
        }
    }
}
