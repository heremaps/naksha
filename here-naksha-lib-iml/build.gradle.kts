plugins {
  alias(libs.plugins.kotlin.multiplatform)
}

description = gatherDescription()

kotlin {
  jvm { }
  sourceSets {
    jvmMain {
      dependencies {
        api(project(":here-naksha-lib-model"))
        implementation(libs.postgres)
      }
    }
    jvmTest {
      dependencies {
        implementation(kotlin("test-junit5"))
        implementation(libs.junit.jupiter)
        runtimeOnly(libs.junit.platform.launcher)
      }
    }
  }
}
