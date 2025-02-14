object Lib {

    val jetbrains_annotations = "org.jetbrains:annotations:24.0.1"

    val vertx_version = "4.5.0"
    val vertx_core = "io.vertx:vertx-core:$vertx_version"
    val vertx_config = "io.vertx:vertx-config:$vertx_version"
    val vertx_auth_jwt = "io.vertx:vertx-auth-jwt:$vertx_version"
    val vertx_redis_client = "io.vertx:vertx-redis-client:$vertx_version"
    val vertx_jdbc_client = "io.vertx:vertx-jdbc-client:$vertx_version"
    val vertx_web = "io.vertx:vertx-web:$vertx_version"
    val vertx_web_openapi = "io.vertx:vertx-web-openapi:$vertx_version"
    val vertx_web_client = "io.vertx:vertx-web-client:$vertx_version"
    val vertx_web_templ = "io.vertx:vertx-web-templ-handlebars:$vertx_version"

    val netty_transport_native_kqueue = "io.netty:netty-transport-native-kqueue:4.1.90.Final"
    val netty_transport_native_epoll = "io.netty:netty-transport-native-epoll:4.1.90.Final"

    val jackson_core = "com.fasterxml.jackson.core:jackson-core:2.15.2"
    val jackson_core_annotations = "com.fasterxml.jackson.core:jackson-annotations:2.15.2"
    val jackson_core_databind = "com.fasterxml.jackson.core:jackson-databind:2.15.2"
    val jackson_core_dataformat = "com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.2"
    val jackson_kotlin = "com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2"

    var snakeyaml = "org.yaml:snakeyaml:1.33";

    val google_flatbuffers = "com.google.flatbuffers:flatbuffers-java:23.5.9"
    val google_protobuf = "com.google.protobuf:protobuf-java:3.16.3"
    val google_guava = "com.google.guava:guava:31.1-jre"
    val google_tink = "com.google.crypto.tink:tink:1.5.0"

    val aws_bom = "software.amazon.awssdk:bom:2.25.19"
    val aws_s3="software.amazon.awssdk:s3"

    val jts_core = "org.locationtech.jts:jts-core:1.19.0"
    val jts_io_common = "org.locationtech.jts.io:jts-io-common:1.19.0"
    val gt_api = "org.geotools:gt-api:19.1"
    val gt_referencing = "org.geotools:gt-referencing:19.1"
    val gt_epsg_hsql = "org.geotools:gt-epsg-hsql:19.1"
    val gt_epsg_extension = "org.geotools:gt-epsg-extension:19.1"

    val spatial4j = "org.locationtech.spatial4j:spatial4j:0.8"

    val slf4j_api = "org.slf4j:slf4j-api:2.0.6"
    val slf4j_console = "org.slf4j:slf4j-simple:2.0.6";
    val jcl_slf4j = "org.slf4j:jcl-over-slf4j:2.0.12"


    val log4j_core = "org.apache.logging.log4j:log4j-core:2.20.0"
    val log4j_api = "org.apache.logging.log4j:log4j-api:2.20.0"
    val log4j_jcl = "org.apache.logging.log4j:log4j-jcl:2.20.0"
    val log4j_slf4j = "org.apache.logging.log4j:log4j-slf4j2-impl:2.20.0"

    val postgres = "org.postgresql:postgresql:42.5.4"
    //val zaxxer_hikari = "com.zaxxer:HikariCP:5.1.0"
    val commons_dbutils = "commons-dbutils:commons-dbutils:1.7"

    val commons_lang3 = "org.apache.commons:commons-lang3:3.12.0"
    val jodah_expiringmap = "net.jodah:expiringmap:0.5.10"
    val caffinitas_ohc = "org.caffinitas.ohc:ohc-core:0.7.4"
    val caffeine = "com.github.ben-manes.caffeine:caffeine:3.2.0"
    val lmax_disruptor = "com.lmax:disruptor:3.4.4"
    val mchange_commons = "com.mchange:mchange-commons-java:0.2.20"
    val mchange_c3p0 = "com.mchange:c3p0:0.9.5.5"

    val jayway_jsonpath = "com.jayway.jsonpath:json-path:2.7.0"
    val jayway_restassured = "com.jayway.restassured:rest-assured:2.9.0"
    val assertj_core = "org.assertj:assertj-core:3.24.2"
    val awaitility = "org.awaitility:awaitility:4.2.0"
    val junit_jupiter_api = "org.junit.jupiter:junit-jupiter-api:5.9.2"
    val junit_jupiter_engine = "org.junit.jupiter:junit-jupiter-engine:5.9.2"
    val junit_jupiter = "org.junit.jupiter:junit-jupiter:5.9.2"
    val junit_params = "org.junit.jupiter:junit-jupiter-params:5.9.2"
    val mockito = "org.mockito:mockito-core:5.8.0"
    val mockito_kotlin = "org.mockito.kotlin:mockito-kotlin:5.3.1"
    val test_containers = "org.testcontainers:testcontainers:1.19.4"
    val test_containers_postgres = "org.testcontainers:postgresql:1.19.4"
    val wiremock =  "org.wiremock:wiremock:3.3.1"

    val flipkart_zjsonpatch = "com.flipkart.zjsonpatch:zjsonpatch:0.4.13"
    val json_assert = "org.skyscreamer:jsonassert:1.5.1"
    val resillience4j_retry = "io.github.resilience4j:resilience4j-retry:2.0.0"

    val otel = "io.opentelemetry:opentelemetry-api:1.28.0"

    val cytodynamics = "com.linkedin.cytodynamics:cytodynamics-nucleus:0.2.0"

    val kotlinx_datetime = "org.jetbrains.kotlinx:kotlinx-datetime:0.5.0"
    val kotlintest_runner_junit5 = "io.kotlintest:kotlintest-runner-junit5:3.3.2"

    val lz4_java = "org.lz4:lz4-java:1.8.0"
}