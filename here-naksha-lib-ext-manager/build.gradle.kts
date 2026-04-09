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
                api(project(":here-naksha-lib-core"))

                implementation(libs.aws.s3)
                implementation(libs.jcl.slf4j)
                implementation(libs.cytodynamics)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.bundles.testing)
                implementation(project(":here-naksha-lib-core"))
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
