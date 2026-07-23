import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.time.Instant

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

val openApiFile = project.projectDir.resolve("src/jvmMain/resources/swagger/openapi.yaml")
tasks.register("updateOpenApiYaml") {
    doLast {
        if (!openApiFile.exists()) throw GradleException("openapi.yaml not found: ${openApiFile.path}")
        val content = openApiFile.readText()
        val currentVersionRegex = Regex("  version: \"([^\"]+)\"")
        val match = currentVersionRegex.find(content)
        val existingVersion = match?.groupValues?.get(1)
        val newVersion = project.version.toString()
        if (existingVersion == newVersion) {
            println("✅ 'openapi.yaml' is up to date: $newVersion")
        } else {
            val updated = content.replace(
                Regex("  version: \"[^\"]+\""),
                "  version: \"${project.version}\""
            )
            openApiFile.writeText(updated)
            println("✅ Updated 'openapi.yaml' to ${project.version} in ${openApiFile.path}")
        }
    }
}

tasks.named("jvmProcessResources") {
    dependsOn("updateOpenApiYaml")
}
