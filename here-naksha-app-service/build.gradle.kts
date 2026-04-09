import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.shadow)
}

description = gatherDescription()
val mainApiClass = "com.here.naksha.app.service.NakshaApp"
val fatJarBaseName = "naksha-app-service"

java {
    setSourceCompatibility(23)
    setTargetCompatibility(23)
}
kotlin {
    jvm {
        mainRun {
            this.mainClass.set(mainApiClass)
        }
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_23)
        }
    }
    sourceSets {
        jvmMain {
            dependencies {
                implementation(project(":here-naksha-lib-core"))
                implementation(project(":here-naksha-lib-psql"))
                implementation(project(":here-naksha-storage-http"))
                //implementation(project(":here-naksha-lib-extension"))
                implementation(project(":here-naksha-lib-hub"))
                implementation(project(":here-naksha-common-http"))
                implementation(project(":here-naksha-lib-diff"))
                implementation(project(":here-naksha-handler-activitylog"))
                implementation(project(":here-naksha-lib-mm-util"))

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
                implementation(project(":here-naksha-lib-core"))
                implementation(libs.bundles.testing)
                implementation(libs.resillience4j.retry)
                implementation(libs.test.containers)
                //implementation(testFixtures(project(":here-naksha-lib-core")))
                implementation(libs.wiremock)
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
    val shadowJar by registering(ShadowJar::class) {
        archiveBaseName.set(fatJarBaseName)
        archiveClassifier.set("")
        archiveVersion.set(project.version.toString())

//        mustRunAfter("testCodeCoverageReport")
//
//        mergeServiceFiles()
//        isZip64 = true

        manifest {
            attributes["Main-Class"] = mainApiClass
        }

        from(kotlin.jvm().compilations.getByName("main").output)
        configurations = listOf(project.configurations.getByName("jvmRuntimeClasspath"))
    }
}
setOverallCoverage(0.0) // only increasing allowed!

