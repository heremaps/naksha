plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

description = gatherDescription()

kotlin {
    jvm {
    }
    js(IR) {
        nodejs()
    }
}
