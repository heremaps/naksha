plugins {
    `kotlin-dsl`
}

group = rootProject.group
version = rootProject.version

repositories {
    maven("https://repo.osgeo.org/repository/release/")
    mavenCentral()
    mavenLocal()
}
