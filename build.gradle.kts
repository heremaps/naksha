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

// to include license files in Jar
tasks.withType<Jar> {
    from(rootProject.file("HERE_NOTICE"))
    into("")
}

tasks.withType<Jar> {
    from(rootProject.file("LICENSE"))
    into("")
}

// Helper, run as `gradle cleanAndTestAll`
tasks.register("cleanAndTestAll") {
    dependsOn(
        // MPP
        ":here-naksha-lib-base:cleanJvmTest",
        ":here-naksha-lib-base:jvmTest",
        ":here-naksha-lib-jbon:cleanJvmTest",
        ":here-naksha-lib-jbon:jvmTest",
        ":here-naksha-lib-geo:cleanJvmTest",
        ":here-naksha-lib-geo:jvmTest",
        ":here-naksha-lib-model:cleanJvmTest",
        ":here-naksha-lib-model:jvmTest",
        ":here-naksha-lib-psql:cleanJvmTest",
        ":here-naksha-lib-psql:jvmTest",
        // Java
        ":here-naksha-lib-core:test",
        ":here-naksha-lib-view:test",
        ":here-naksha-lib-diff:test",
        ":here-naksha-lib-handlers:test",
        ":here-naksha-lib-hub:test",
        ":here-naksha-lib-ext-manager:test",
        ":here-naksha-storage-http:test",
        ":here-naksha-handler-activitylog:test",
    )
}
