import com.here.naksha.lib.jbon.*
import JsonConverter.jsonToJbonByteArray
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import kotlin.test.BeforeTest
import kotlin.test.assertEquals

class JsDbJbonTest : JvmTestContainer() {

    @Test
    fun testSql_jb_get_bool() {
        // given
        val featureBytea = jsonToJbonByteArray("""{"bool":true}""");
        val plan = JbSession.get().sql().prepare(
                "SELECT jb_get_bool($1,$2,$3)",
                SQL_BYTE_ARRAY, SQL_STRING, SQL_BOOLEAN
        )
        // when
        query(plan, featureBytea, "bool", false) {
            // then
            assertEquals(true, it["jb_get_bool"])
        }
    }

    @Order(1)
    @Test
    fun testSql_jb_get_int4() {
        // given
        val featureBytea = jsonToJbonByteArray("""{"id":123}""");
        val plan = JbSession.get().sql().prepare(
                "SELECT jb_get_int4($1,$2,$3)",
                SQL_BYTE_ARRAY, SQL_STRING, SQL_INT32
        )
        // when
        query(plan, featureBytea, "id", 0) {
            // then
            assertEquals(123, it["jb_get_int4"])
        }
    }

    @Order(1)
    @Test
    fun testSql_jb_get_real() {
        // given
        val featureBytea = jsonToJbonByteArray("""{"x":0.1}""");
        val plan = JbSession.get().sql().prepare(
                "SELECT jb_get_real($1,$2,$3)",
                SQL_BYTE_ARRAY, SQL_STRING, SQL_FLOAT32
        )
        // when
        query(plan, featureBytea, "x", 0.0f) {
            // then
            assertEquals(0.1f, it["jb_get_real"])
        }
    }

    @Order(1)
    @Test
    fun testSql_jb_get_double() {
        // given
        val featureBytea = jsonToJbonByteArray("""{"x":11.5}""");
        val plan = JbSession.get().sql().prepare(
                "SELECT jb_get_double($1,$2,$3)",
                SQL_BYTE_ARRAY, SQL_STRING, SQL_FLOAT64
        )

        // when
        query(plan, featureBytea, "x", 0.0) {
            // then
            assertEquals(11.5, it["jb_get_double"])
        }
    }

    @Order(1)
    @Test
    fun testSql_jb_get_text() {
        // given
        val featureBytea = jsonToJbonByteArray("""{"name":"awesome!"}""");
        val plan = JbSession.get().sql().prepare(
                "SELECT jb_get_text($1,$2,$3)",
                SQL_BYTE_ARRAY, SQL_STRING, SQL_STRING
        )
        // when
        query(plan, featureBytea, "name", "none") {
            // then
            assertEquals("awesome!", it["jb_get_text"])
        }
    }

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
                "SELECT jb_get_text($1,$2,$3)",
                SQL_BYTE_ARRAY, SQL_STRING, SQL_STRING
        )
        // when
        query(plan, featureBytea, "properties.@:a:b:c.tag", "") {
            // then
            assertEquals("bingo!", it["jb_get_text"])
        }
    }

    @Test
    fun testSql_jb_get_text_Alternative_null() {
        // given
        val featureBytea = jsonToJbonByteArray("""{"x":"a"}""");
        val plan = JbSession.get().sql().prepare(
                "SELECT jb_get_text($1,$2,$3)",
                SQL_BYTE_ARRAY, SQL_STRING, SQL_STRING
        )
        // when
        query(plan, featureBytea, "fake", null) {
            // then
            assertEquals(null, it["jb_get_text"])
        }
    }

    @Test
    fun testSql_jb_get_text_Alternative_other() {
        // given
        val featureBytea = jsonToJbonByteArray("""{"x":"a"}""");
        val plan = JbSession.get().sql().prepare(
                "SELECT jb_get_text($1,$2,$3)",
                SQL_BYTE_ARRAY, SQL_STRING, SQL_STRING
        )
        // when
        query(plan, featureBytea, "fake", "altText") {
            // then
            assertEquals("altText", it["jb_get_text"])
        }
    }

    private fun query(plan: ISqlPlan, vararg args: Any?, assertion: (Map<String, Any>) -> Unit) {
        try {
            val cursor = plan.cursor(*args)
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