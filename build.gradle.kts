plugins {
    id("naksha.java")
    id("naksha.publish")
    // https://github.com/johnrengelman/shadow
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

configurations.implementation {
    exclude(module = "commons-logging")
}

// Create the fat jar for the whole Naksha.
rootProject.dependencies {
    //This is needed, otherwise the blank root project will include nothing in the fat jar
    implementation(project(":here-naksha-app-service"))
}

// To include license files in Jar.
tasks.withType<Jar> {
    from(rootProject.file("HERE_NOTICE"))
    into("")
}

tasks.withType<Jar> {
    from(rootProject.file("LICENSE"))
    into("")
}

enum class CleanAndTest {
    // Do not run tests
    OFF,
    // Run kotlin.test
    KOTLIN,
    // Run jvmTest
    JAVA
}

enum class PublishModule {
    // do NOT publish the module to artifactory
    NO,
    // publish the module to artifactory
    YES
}

val modulesToTest = mapOf(
    Pair(":here-naksha-lib-base", Pair(CleanAndTest.KOTLIN, PublishModule.YES)),
    Pair(":here-naksha-lib-jbon", Pair(CleanAndTest.KOTLIN, PublishModule.YES)),
    Pair(":here-naksha-lib-geo", Pair(CleanAndTest.KOTLIN, PublishModule.YES)),
    Pair(":here-naksha-lib-model", Pair(CleanAndTest.KOTLIN, PublishModule.YES)),
    Pair(":here-naksha-lib-psql", Pair(CleanAndTest.KOTLIN, PublishModule.YES)),
    Pair(":here-naksha-lib-auth", Pair(CleanAndTest.KOTLIN, PublishModule.YES)),
    Pair(":here-naksha-lib-core", Pair(CleanAndTest.JAVA, PublishModule.NO)),
    Pair(":here-naksha-lib-view", Pair(CleanAndTest.JAVA, PublishModule.NO)),
    Pair(":here-naksha-lib-diff", Pair(CleanAndTest.JAVA, PublishModule.NO)),
    Pair(":here-naksha-lib-handlers", Pair(CleanAndTest.JAVA, PublishModule.NO)),
    Pair(":here-naksha-lib-hub", Pair(CleanAndTest.JAVA, PublishModule.NO)),
    Pair(":here-naksha-lib-ext-manager", Pair(CleanAndTest.JAVA, PublishModule.NO)),
    Pair(":here-naksha-storage-http", Pair(CleanAndTest.JAVA, PublishModule.NO)),
    Pair(":here-naksha-handler-activitylog", Pair(CleanAndTest.JAVA, PublishModule.NO)),
)

// Helper, run as `gradle cleanAndTestAll`
tasks.register("cleanAndTestAll") {
    //apply(plugin = "naksha.spotless-kotlin")
    modulesToTest.forEach {
        when (it.value.first) {
            CleanAndTest.KOTLIN -> {
                dependsOn("${it.key}:cleanJvmTest")
                dependsOn("${it.key}:jvmTest")
            }
            CleanAndTest.JAVA -> {
                dependsOn("${it.key}:test")
                dependsOn("${it.key}:jacocoTestReport")
                //dependsOn("${it.key}:jacocoTestCoverageVerification")
            }
            else -> {}
        }
    }
}

// Helper, run as `gradle publishToLocal`
tasks.register("publishToLocal") {
    modulesToTest.forEach {
        when (it.value.second) {
            PublishModule.YES -> {
                dependsOn("${it.key}:publishJvmPublicationToMavenLocal")
                dependsOn("${it.key}:publishKotlinMultiplatformPublicationToMavenLocal")
            }
            else -> {}
        }
    }
}

// Helper, run as `gradle publishToHere`
tasks.register("publishToHere") {
    modulesToTest.forEach {
        when (it.value.second) {
            PublishModule.YES -> {
                dependsOn("${it.key}:clean")
                dependsOn("${it.key}:publishJvmPublicationToMavenRepository")
                dependsOn("${it.key}:publishKotlinMultiplatformPublicationToMavenRepository")
            }
            else -> {}
        }
    }
}

//tasks.register("fixLicense") {
//    subprojects.forEach { module ->
//        val info = modulesToTest[module.name]
//        println("Apply spotless to ${module.name}")
//        // TODO: Apply spotless
//        // id("naksha.spotless-kotlin")
//    }
//}
