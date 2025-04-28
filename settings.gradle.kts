pluginManagement {
    repositories {
        //mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "naksha"

include(":here-naksha-lib-base")
include(":here-naksha-lib-geo")
include(":here-naksha-lib-auth")
include(":here-naksha-lib-model")
include(":here-naksha-lib-jbon")
include(":here-naksha-lib-psql")
include(":here-naksha-lib-core")
//include(":here-naksha-lib-heapcache")
//include(":here-naksha-lib-extension")
include(":here-naksha-handler-activitylog")
//include(":here-naksha-handler-http")
include(":here-naksha-lib-handlers")
include(":here-naksha-lib-hub")
include(":here-naksha-lib-view")
include(":here-naksha-common-http")
include(":here-naksha-storage-http")
include(":here-naksha-app-service")
include(":here-naksha-lib-ext-manager")
include(":here-naksha-lib-diff")
