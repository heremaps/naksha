import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    kotlin("plugin.js-plain-objects")
}

kotlin {
    jvm {
        withJava()
    }

    js(IR) {
        moduleName = "psql"
        browser {
            webpackTask {
                output.libraryTarget = "commonjs2"
                output.library = "naksha.psql"
            }
        }
        useEsModules()
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            target.set("es2015")
        }
        nodejs {
        }
        generateTypeScriptDefinitions()
        binaries.library() // gradle jsBrowserProductionLibraryDistribution
        binaries.executable() // gradle jsBrowserProductionWebpack
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(kotlin("stdlib-common"))
                api(project(":here-naksha-lib-base"))
                api(project(":here-naksha-lib-jbon"))
                api(project(":here-naksha-lib-model"))
                api(project(":here-naksha-lib-geo"))

                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
            }
        }
        jvmMain {
            jvmToolchain(11)
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
                api(project(":here-naksha-lib-base"))
                api(project(":here-naksha-lib-jbon"))
                api(project(":here-naksha-lib-model"))
                api(project(":here-naksha-lib-geo"))

                implementation("org.apache.commons:commons-lang3:3.12.0")
                implementation("org.postgresql:postgresql:42.5.4")
                implementation("org.testcontainers:postgresql:1.19.4")
                implementation("commons-dbutils:commons-dbutils:1.7")
                implementation("org.locationtech.jts:jts-core:1.19.0")
                implementation("org.locationtech.jts.io:jts-io-common:1.19.0")

                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
                implementation("org.postgresql:postgresql:42.5.4")
            }
            // TODO: We should replace ${project.buildDir} with ${layout.buildDirectory}, but this is not the same:
            // println("------------ ${project.buildDir}/dist/js/productionExecutable/")
            // println("------------ ${layout.buildDirectory}/dist/js/productionExecutable/")
            resources.setSrcDirs(resources.srcDirs + "${project.buildDir}/dist/js/productionExecutable/")
        }
        jvmTest {
            dependencies {
                implementation(kotlin("test"))
                implementation("io.kotlintest:kotlintest-runner-junit5:3.3.2")
                runtimeOnly("org.junit.jupiter:junit-jupiter-engine:5.5.2")
                implementation("org.junit.jupiter:junit-jupiter-api:5.5.2")
                implementation("org.junit.jupiter:junit-jupiter-params:5.5.2")
                implementation("org.testcontainers:postgresql:1.19.4")
                implementation("org.postgresql:postgresql:42.5.4")
                implementation(project(":here-naksha-lib-jbon"))
                implementation("org.mockito:mockito-core:5.8.0")
                implementation("org.mockito.kotlin:mockito-kotlin:5.3.1")
                implementation("org.locationtech.spatial4j:spatial4j:0.8")
            }
        }
        jsMain {
            dependencies {
                implementation(kotlin("stdlib-js"))
                api(project(":here-naksha-lib-base"))
                api(project(":here-naksha-lib-jbon"))
                api(project(":here-naksha-lib-model"))
                api(project(":here-naksha-lib-geo"))

                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
                //implementation(npm("postgres", "3.4.4"))
            }
        }
    }
}

configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks {
    val jsProductionLibraryCompileSync = getByName<Task>("jsProductionLibraryCompileSync")
    val jsProductionExecutableCompileSync = getByName<Task>("jsProductionExecutableCompileSync")
    val browserDistribution = getByName<Task>("jsBrowserDistribution")
    val webpackTask = getByName<KotlinWebpack>("jsBrowserProductionWebpack") {
        dependsOn(jsProductionLibraryCompileSync)
    }
    getByName<Task>("jsNodeProductionLibraryDistribution") {
        dependsOn(jsProductionExecutableCompileSync)
    }
    getByName<Task>("jsBrowserProductionLibraryDistribution") {
        dependsOn(jsProductionExecutableCompileSync)
    }
    getByName<Test>("jvmTest") {
        useJUnitPlatform()
        maxHeapSize = "8g"
    }
    getByName<Jar>("jvmJar") {
        dependsOn(webpackTask)
        from({
            val list = ArrayList<Any>()
            configurations.runtimeClasspath.get().forEach {
                val f = if (it.isDirectory()) it else zipTree(it)
                list.add(f)
            }
            list
        }).duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    getByName<ProcessResources>("jvmProcessResources") {
        dependsOn(webpackTask, browserDistribution)
    }
    getByName<ProcessResources>("jvmTestProcessResources") {
        dependsOn(webpackTask)
    }
}