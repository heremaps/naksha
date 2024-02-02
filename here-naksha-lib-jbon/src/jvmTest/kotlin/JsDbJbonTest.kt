import com.here.naksha.lib.jbon.*
import com.here.naksha.lib.jbon.JsonConverter.jsonToJbonByteArray
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.DriverManager
import kotlin.test.assertEquals

class JsDbJbonTest {
    companion object {

        private lateinit var postgreSQLContainer: PostgreSQLContainer<*>

        @JvmStatic
        @BeforeAll
        fun initJvm(): Unit {
            val image = DockerImageName.parse("greenoag/postgres-plv8-postgis:15.2-3.1.5-3.3").asCompatibleSubstituteFor("postgres")
            postgreSQLContainer = PostgreSQLContainer(image)
                    .withDatabaseName("plv8-db")
                    .withUsername("user")
                    .withPassword("password")

            postgreSQLContainer.start()

            JvmSession.register()
            // Install into database.
            // Only run SQL tests when
            val dbUrl = "${postgreSQLContainer.getJdbcUrl()}?&user=${postgreSQLContainer.username}&password=${postgreSQLContainer.password}"

            // Load PostgresQL driver.
            Class.forName("org.postgresql.Driver");
            val connection = DriverManager.getConnection(dbUrl)
            connection.autoCommit = false
            JvmSession.jvmGetter.get().sqlSet(JvmSql(connection))
            enableSqlTests = true

            val session = JvmSession.jvmGetter.get()
            session.installModules()
            session.sqlCommit()
        }

        private var enableSqlTests: Boolean = false

        @JvmStatic
        fun runSqlTests(): Boolean {
            return enableSqlTests
        }

        @JvmStatic
        @AfterAll
        fun afterAll() {
            postgreSQLContainer.stop()
        }
    }

    @EnabledIf("runSqlTests")
    @Test
    fun testSql_jb_get_bool() {
        // given
        val featureBytea = jsonToJbonByteArray("""{"bool":true}""");
        val plan = JbSession.get().sql().prepare(
                "SELECT commonjs2_init(), jb_get_bool($1,$2,$3)",
                arrayOf(SQL_BYTE_ARRAY, SQL_STRING, SQL_BOOLEAN)
        )
        val args = arrayOf(featureBytea, "bool", false)

        // when
        query(plan, args) {
            // then
            assertEquals(true, it["jb_get_bool"])
        }
    }

    @EnabledIf("runSqlTests")
    @Test
    fun testSql_jb_get_int4() {
        // given
        val featureBytea = jsonToJbonByteArray("""{"id":123}""");
        val plan = JbSession.get().sql().prepare(
                "SELECT commonjs2_init(), jb_get_int4($1,$2,$3)",
                arrayOf(SQL_BYTE_ARRAY, SQL_STRING, SQL_INT32)
        )
        val args = arrayOf(featureBytea, "id", 0)

        // when
        query(plan, args) {
            // then
            assertEquals(123, it["jb_get_int4"])
        }
    }

    @EnabledIf("runSqlTests")
    @Test
    fun testSql_jb_get_real() {
        // given
        val featureBytea = jsonToJbonByteArray("""{"x":0.1}""");
        val plan = JbSession.get().sql().prepare(
                "SELECT commonjs2_init(), jb_get_real($1,$2,$3)",
                arrayOf(SQL_BYTE_ARRAY, SQL_STRING, SQL_FLOAT32)
        )
        val args = arrayOf(featureBytea, "x", 0.0f)

        // when
        query(plan, args) {
            // then
            assertEquals(0.1f, it["jb_get_real"])
        }
    }

    @EnabledIf("runSqlTests")
    @Test
    fun testSql_jb_get_double() {
        // given
        val featureBytea = jsonToJbonByteArray("""{"x":11.5}""");
        val plan = JbSession.get().sql().prepare(
                "SELECT commonjs2_init(), jb_get_double($1,$2,$3)",
                arrayOf(SQL_BYTE_ARRAY, SQL_STRING, SQL_FLOAT64)
        )
        val args = arrayOf(featureBytea, "x", 0.0)

        // when
        query(plan, args) {
            // then
            assertEquals(11.5, it["jb_get_double"])
        }
    }

    @EnabledIf("runSqlTests")
    @Test
    fun testSql_jb_get_text() {
        // given
        val featureBytea = jsonToJbonByteArray("""{"name":"awesome!"}""");
        val plan = JbSession.get().sql().prepare(
                "SELECT commonjs2_init(), jb_get_text($1,$2,$3)",
                arrayOf(SQL_BYTE_ARRAY, SQL_STRING, SQL_STRING)
        )
        val args = arrayOf(featureBytea, "name", "none")

        // when
        query(plan, args) {
            // then
            assertEquals("awesome!", it["jb_get_text"])
        }
    }

    @EnabledIf("runSqlTests")
    @Test
    fun testSql_jb_get_text_deep_in_tree() {
        // given
        val featureBytea = jsonToJbonByteArray("""
            {
                "name":"awesome!",
                "properties": {
                    "@:a:b:c": {
                        "tag": "bingo!"
                    }
                }
             }
            """.trimIndent());
        val plan = JbSession.get().sql().prepare(
                "SELECT commonjs2_init(), jb_get_text($1,$2,$3)",
                arrayOf(SQL_BYTE_ARRAY, SQL_STRING, SQL_STRING)
        )
        val args = arrayOf(featureBytea, "properties.@:a:b:c.tag", "")

        // when
        query(plan, args) {
            // then
            assertEquals("bingo!", it["jb_get_text"])
        }
    }

    @EnabledIf("runSqlTests")
    @Test
    fun testSql_jb_get_text_Alternative_null() {
        // given
        val featureBytea = jsonToJbonByteArray("""{"x":"a"}""");
        val plan = JbSession.get().sql().prepare(
                "SELECT commonjs2_init(), jb_get_text($1,$2,$3)",
                arrayOf(SQL_BYTE_ARRAY, SQL_STRING, SQL_STRING)
        )
        val args = arrayOf(featureBytea, "fake", null)

        // when
        query(plan, args) {
            // then
            assertEquals(null, it["jb_get_text"])
        }
    }

    @EnabledIf("runSqlTests")
    @Test
    fun testSql_jb_get_text_Alternative_other() {
        // given
        val featureBytea = jsonToJbonByteArray("""{"x":"a"}""");
        val plan = JbSession.get().sql().prepare(
                "SELECT commonjs2_init(), jb_get_text($1,$2,$3)",
                arrayOf(SQL_BYTE_ARRAY, SQL_STRING, SQL_STRING)
        )
        val args = arrayOf(featureBytea, "fake", "altText")

        // when
        query(plan, args) {
            // then
            assertEquals("altText", it["jb_get_text"])
        }
    }

    private fun query(plan: ISqlPlan, args: Array<*>, assertion: (Map<String, Any>) -> Unit) {
        try {
            val cursor = plan.cursor(args as Array<Any?>)
            try {
                val row = cursor.next() as Map<String, Any>
                assertion(row)
            } finally {
                cursor.close()
            }
        } finally {
            plan.close()
        }
    }
}