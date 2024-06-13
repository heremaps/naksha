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
        moduleName = "plv8"
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
                implementation(project(":here-naksha-lib-jbon"))
                implementation(project(":here-naksha-lib-base"))
                implementation(project(":here-naksha-lib-model"))
                implementation(project(":here-naksha-lib-geo"))
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
            }
        }
        commonMain {
            dependencies {
            }
        }
        jvmMain {
            dependencies {
                api(project(":here-naksha-lib-geo"))
                api(project(":here-naksha-lib-model"))
                api(project(":here-naksha-lib-core"))

                implementation(kotlin("stdlib-jdk8"))
                implementation(project(":here-naksha-lib-jbon"))
                implementation(project(":here-naksha-lib-base"))

                implementation("org.apache.commons:commons-lang3:3.12.0")
                implementation("org.postgresql:postgresql:42.5.4")
                implementation("commons-dbutils:commons-dbutils:1.7")
                implementation("org.locationtech.jts:jts-core:1.19.0")
                implementation("org.locationtech.jts.io:jts-io-common:1.19.0")

                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
                implementation("org.postgresql:postgresql:42.5.4")
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
                implementation("org.testcontainers:postgresql:1.19.4")
                implementation("org.postgresql:postgresql:42.5.4")
                implementation(project(":here-naksha-lib-jbon"))
                implementation("org.mockito:mockito-core:5.8.0")
                implementation("org.locationtech.spatial4j:spatial4j:0.8")
            }
        }
        jsMain {
            dependencies {
                implementation(kotlin("stdlib-js"))
                implementation(project(":here-naksha-lib-jbon"))
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
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
        dependsOn(webpackTask)
    }
    getByName<ProcessResources>("jvmTestProcessResources") {
        dependsOn(webpackTask, browserDistribution)
    }
}