import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack

plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    jvm {
        //jvmToolchain(11)
        withJava()
    }

    js(IR) {
        moduleName = "jbon"
        browser {
            webpackTask {
                output.libraryTarget = "commonjs2"
            }
        }
        useEsModules()
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
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
            }
        }
        jvmMain {
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
                api("org.lz4:lz4-java:1.8.0")
            }
            resources.setSrcDirs(resources.srcDirs + "$buildDir/dist/js/productionExecutable/")
        }
        jvmTest {
            dependencies {
                implementation(kotlin("test"))
                implementation("io.kotlintest:kotlintest-runner-junit5:3.3.2")
                runtimeOnly("org.junit.jupiter:junit-jupiter-engine:5.5.2")
                implementation("org.junit.jupiter:junit-jupiter-api:5.5.2")
                implementation("org.junit.jupiter:junit-jupiter-params:5.5.2")
            }
            resources.setSrcDirs(resources.srcDirs + "$buildDir/dist/js/productionExecutable/")
        }
        jsMain {
            dependencies {
                implementation(kotlin("stdlib-js"))
            }
        }
    }
}

configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks {
    val webpackTask = getByName<KotlinWebpack>("jsBrowserProductionWebpack")
    val browserDistribution = getByName<Task>("jsBrowserDistribution")
    getByName<Test>("jvmTest") {
        useJUnitPlatform()
        maxHeapSize = "8g"
    }
    getByName<Jar>("jvmJar") {
        dependsOn(webpackTask)
    }
    getByName<ProcessResources>("jvmProcessResources") {
        dependsOn(webpackTask)
    }
    getByName<ProcessResources>("jvmTestProcessResources") {
        dependsOn(webpackTask, browserDistribution)
    }
}
