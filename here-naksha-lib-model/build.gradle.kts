import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    kotlin("plugin.js-plain-objects")
}

kotlin {
    jvm {
        //jvmToolchain(11)
        withJava()
    }

    js(IR) {
        moduleName = "model"
        browser {
            webpackTask {
                output.libraryTarget = "commonjs2"
                output.library = "naksha.model"
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
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
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
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
            }
        }
        jvmMain {
            jvmToolchain(11)
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
                api(project(":here-naksha-lib-base"))
                api(project(":here-naksha-lib-geo"))
                api(project(":here-naksha-lib-jbon"))
								api(project(":here-naksha-lib-auth"))
            }
            resources.setSrcDirs(resources.srcDirs + "$buildDir/dist/js/productionExecutable/")
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
    getByName<ProcessResources>("jvmProcessResources") {
        dependsOn(webpackTask, browserDistribution)
    }
    getByName<ProcessResources>("jvmTestProcessResources") {
        dependsOn(webpackTask, browserDistribution)
    }
    getByName<Test>("jvmTest") {
        useJUnitPlatform()
        maxHeapSize = "8g"
    }
    getByName<Jar>("jvmJar") {
        dependsOn(webpackTask)
    }
}