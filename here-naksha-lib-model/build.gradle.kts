import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JsModuleKind
import org.jetbrains.kotlin.gradle.dsl.JsSourceMapEmbedMode
import org.jetbrains.kotlin.gradle.dsl.JsSourceMapNamesPolicy
import java.time.Instant

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

description = gatherDescription()

kotlin {
    jvm {
        compilerOptions {
            freeCompilerArgs = listOf("-Xjvm-default=all")
        }
    }
    js { }
    sourceSets {
        commonMain {
            dependencies {
                implementation(kotlin("stdlib"))
                implementation(libs.kotlinx.datetime)
                api(project(":here-naksha-lib-base"))
                api(project(":here-naksha-lib-geo"))
                api(project(":here-naksha-lib-jbon"))
                api(project(":here-naksha-lib-auth"))
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
                implementation("org.mockito:mockito-core:5.13.0")
                implementation(libs.kotlinx.datetime)
            }
        }
        jvmMain {
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
                api(project(":here-naksha-lib-base"))
                api(project(":here-naksha-lib-geo"))
                api(project(":here-naksha-lib-jbon"))
								api(project(":here-naksha-lib-auth"))
            }
            resources.setSrcDirs(resources.srcDirs + "${layout.buildDirectory}/dist/js/productionExecutable/")
        }
        jvmTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlintest.runner.junit5)
                implementation(libs.mockito)
                runtimeOnly(libs.junit.jupiter.engine)
                runtimeOnly(libs.junit.platform.launcher) // https://github.com/gradle/gradle/issues/34512
            }
        }
        jsMain {
            dependencies {
                implementation(kotlin("stdlib-js"))
                api(project(":here-naksha-lib-base"))
                api(project(":here-naksha-lib-geo"))
                api(project(":here-naksha-lib-jbon"))
								api(project(":here-naksha-lib-auth"))
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
