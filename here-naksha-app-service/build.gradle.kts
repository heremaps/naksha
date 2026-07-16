import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.shadow)
}

description = gatherDescription()
val mainApiClass = "com.here.naksha.app.service.NakshaApp"
val fatJarBaseName = "naksha-app-service"

kotlin {
    jvm {
        @OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
        mainRun {
            this.mainClass.set(mainApiClass)
        }
    }
    sourceSets {
        jvmMain {
            dependencies {
                api(project(":here-naksha-lib-core"))
                api(project(":here-naksha-lib-psql"))
                api(project(":here-naksha-storage-http"))
                api(project(":here-naksha-lib-hub"))
                api(project(":here-naksha-common-http"))
                api(project(":here-naksha-lib-diff"))
                api(project(":here-naksha-handler-activitylog"))
                api(project(":here-naksha-lib-mm-util"))

                implementation(libs.commons.lang3)
                implementation(libs.otel)
                implementation(libs.postgres)
                implementation(libs.bundles.logging)
                implementation(libs.bundles.vertx)
                implementation(libs.bundles.spatial)
                implementation(libs.bundles.jackson)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.bundles.testing)
                implementation(libs.resillience4j.retry)
                implementation(libs.test.containers)
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
    named<ShadowJar>("shadowJar") {
        archiveBaseName = fatJarBaseName
        archiveClassifier = ""
        archiveVersion = project.version.toString()

//        mustRunAfter("testCodeCoverageReport")
//
//        mergeServiceFiles()
//        isZip64 = true

        manifest {
            attributes["Main-Class"] = mainApiClass
        }
    }
}
setOverallCoverage(0.0) // only increasing allowed!

