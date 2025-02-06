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