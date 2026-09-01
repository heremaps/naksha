import java.time.Instant

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
                api(project(":here-naksha-lib-base"))
                api(project(":here-naksha-lib-geo"))
                api(project(":here-naksha-lib-jbon"))
                api(project(":here-naksha-lib-auth"))
            }
        }
        jvmMain {
            dependencies {
            }
            resources.setSrcDirs(resources.srcDirs + "${layout.buildDirectory}/dist/js/productionExecutable/")
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
            }
        }
    }
}

val versionJsonFile = project.projectDir.resolve("src/jvmMain/resources/version.json")
val nakshaVersionFile = project.projectDir.resolve("src/commonMain/kotlin/naksha/model/NakshaVersion.kt")
tasks.register("generateVersionFile") {
    doLast {
        val newJson = """{
  "version": "${project.version}",
  "buildTime": "${Instant.now()}"
}""".trimIndent()
        versionJsonFile.parentFile.mkdirs()
        versionJsonFile.writeText(newJson)
        println("✅ Generated ${versionJsonFile.path}: $newJson")

        if (!nakshaVersionFile.exists()) throw GradleException("NakshaVersion class not found: ${nakshaVersionFile.path}")
        val currentVersionRegex = Regex("""const val CURRENT = "([^"]+)"""")
        val content = nakshaVersionFile.readText()
        val match = currentVersionRegex.find(content)
        val existingVersion = match?.groupValues?.get(1)
        val newVersion = project.version.toString()
        if (existingVersion == newVersion) {
            println("✅ NakshaVersion.kt is up to date: $newVersion")
        } else {
            val updated = content.replace(
                Regex("""const val CURRENT = "[^"]+""""),
                """const val CURRENT = "${project.version}""""
            )
            nakshaVersionFile.writeText(updated)
            println("✅ Updated CURRENT to ${project.version} in ${nakshaVersionFile.path}")
        }

    }
}

tasks.named("jvmProcessResources") {
    dependsOn("generateVersionFile")
}

tasks.matching { it.name == "jsNodeTest" }.configureEach {
    enabled = false
}
