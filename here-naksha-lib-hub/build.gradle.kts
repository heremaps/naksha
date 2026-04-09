plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

description = gatherDescription()

java {
    setSourceCompatibility(23)
    setTargetCompatibility(23)
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_23)
        }
    }
    sourceSets {
        jvmMain {
            dependencies {
                implementation(project(":here-naksha-lib-core"))
                implementation(project(":here-naksha-lib-model"))
                implementation(project(":here-naksha-lib-psql"))
                implementation(project(":here-naksha-lib-handlers"))
                implementation(project(":here-naksha-lib-ext-manager"))

                implementation(libs.commons.lang3)
                implementation(libs.jts.core)
                implementation(libs.postgres)
                implementation(libs.aws.s3)

                implementation(libs.bundles.jackson)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.json.assert)
                implementation(libs.bundles.testing)
                runtimeOnly(libs.junit.platform.launcher) // https://github.com/gradle/gradle/issues/34512
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
