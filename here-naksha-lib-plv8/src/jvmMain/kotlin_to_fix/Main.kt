package com.here.naksha.lib.plv8

import java.sql.DriverManager

// java -cp build/libs/here-naksha-lib-plv8-jvm-3.0.0-alpha.5.jar com.here.naksha.lib.plv8.MainKt
// java -cp here-naksha-lib-plv8-jvm-3.0.0-alpha.5.jar com.here.naksha.lib.plv8.MainKt
fun main() {
    println("Enter JDBC url (enter -> jdbc:postgresql://localhost:5400/postgres?user=postgres&password=password)")
    var url : String?
    var schema : String?
    try {
        url = readln()
    } catch (e: Exception) {
        url = System.getenv("NAKSHA_DB_URL")
        println("EOF, read env variable NAKSHA_DB_URL = $url")
    }
    if (url.isNullOrEmpty()) url = "jdbc:postgresql://localhost:5400/postgres?user=postgres&password=password"
    println("Enter schema to use (enter -> test_schema):")
    try {
        schema = readln()
    } catch (e:Exception) {
        schema = System.getenv("NAKSHA_DB_SCHEMA")
        println("EOF, read env variable NAKSHA_DB_SCHEMA = $schema")
    }
    if (schema.isNullOrEmpty()) schema = "test_schema"
    url += "&schema=$schema"

    println("Use DB URL: $url")

    JvmPlv8Env.initialize()
    val env = JvmPlv8Env.get()
    val conn = DriverManager.getConnection(url)
    env.install(conn, 0, schema, "test_storage", "plv8_test")
    env.startSession(
            conn,
            schema,
            "plv8_test",
            env.randomString(),
            "plv8_test_app",
            "plv8_test_user"
    )
    conn.commit()
}