import com.here.naksha.lib.jbon.*
import com.here.naksha.lib.plv8.IPlv8Plan
import com.here.naksha.lib.plv8.NakshaSession
import com.here.naksha.lib.plv8.Static
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@Suppress("UNCHECKED_CAST")
class Plv8JbonTest : Plv8TestContainer() {
    private val builder = JbBuilder(newDataView(16384))

    private fun jsonToJbonByteArray(json: String): ByteArray {
        builder.clear()
        val raw = env.parse(json)
        return builder.buildFeatureFromMap(asMap(raw))
    }

    @Test
    fun testSql_jb_get_bool() {
        // given
        val session = NakshaSession.get()
        val featureBytea = jsonToJbonByteArray("""{"bool":true}""");
        val plan = session.sql.prepare(
                "SELECT jb_get_bool($1,$2,$3)",
                arrayOf(SQL_BYTE_ARRAY, SQL_STRING, SQL_BOOLEAN)
        )
        // when
        query(plan, arrayOf(featureBytea, "bool", false)) {
            // then
            assertEquals(true, it["jb_get_bool"])
        }
    }

    @Order(1)
    @Test
    fun testSql_jb_get_int4() {
        // given
        val session = NakshaSession.get()
        val featureBytea = jsonToJbonByteArray("""{"idi":123}""");
        val plan = session.sql.prepare(
                "SELECT jb_get_int4($1,$2,$3)",
                arrayOf(SQL_BYTE_ARRAY, SQL_STRING, SQL_INT32)
        )
        // when
        query(plan, arrayOf(featureBytea, "idi", 0)) {
            // then
            assertEquals(123, it["jb_get_int4"])
        }
    }

    @Order(1)
    @Test
    fun testSql_jb_get_double() {
        // given
        val session = NakshaSession.get()
        val featureBytea = jsonToJbonByteArray("""{"x":11.5}""");
        val plan = session.sql.prepare(
                "SELECT jb_get_double($1,$2,$3)",
                arrayOf(SQL_BYTE_ARRAY, SQL_STRING, SQL_FLOAT64)
        )

        // when
        query(plan, arrayOf(featureBytea, "x", 0.0)) {
            // then
            assertEquals(11.5, it["jb_get_double"])
        }
    }

    @Order(1)
    @Test
    fun testSql_jb_get_text() {
        // given
        val session = NakshaSession.get()
        val featureBytea = jsonToJbonByteArray("""{"name":"awesome!"}""");
        val plan = session.sql.prepare(
                "SELECT jb_get_text($1,$2,$3)",
                arrayOf(SQL_BYTE_ARRAY, SQL_STRING, SQL_STRING)
        )
        // when
        query(plan, arrayOf(featureBytea, "name", "none")) {
            // then
            assertEquals("awesome!", it["jb_get_text"])
        }
    }

    @Test
    fun testSql_jb_get_text_deep_in_tree() {
        // given
        val session = NakshaSession.get()
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
        val plan = session.sql.prepare(
                "SELECT jb_get_text($1,$2,$3)",
                arrayOf(SQL_BYTE_ARRAY, SQL_STRING, SQL_STRING)
        )
        // when
        query(plan, arrayOf(featureBytea, "properties.@:a:b:c.tag", "")) {
            // then
            assertEquals("bingo!", it["jb_get_text"])
        }
    }

    @Test
    fun testSql_jb_get_text_Alternative_null() {
        // given
        val session = NakshaSession.get()
        val featureBytea = jsonToJbonByteArray("""{"x":"a"}""");
        val plan = session.sql.prepare(
                "SELECT jb_get_text($1,$2,$3)",
                arrayOf(SQL_BYTE_ARRAY, SQL_STRING, SQL_STRING)
        )
        // when
        query(plan, arrayOf(featureBytea, "fake", null)) {
            // then
            assertEquals(null, it["jb_get_text"])
        }
    }

    @Test
    fun testSql_jb_get_text_Alternative_other() {
        // given
        val session = NakshaSession.get()
        val featureBytea = jsonToJbonByteArray("""{"x":"a"}""");
        val plan = session.sql.prepare(
                "SELECT jb_get_text($1,$2,$3)",
                arrayOf(SQL_BYTE_ARRAY, SQL_STRING, SQL_STRING)
        )
        // when
        query(plan, arrayOf(featureBytea, "fake", "altText")) {
            // then
            assertEquals("altText", it["jb_get_text"])
        }
    }

    private fun query(plan: IPlv8Plan, args: Array<Any?>?, assertion: (Map<String, Any?>) -> Unit) {
        try {
            val cursor = if (!args.isNullOrEmpty()) plan.cursor(args) else plan.cursor()
            try {
                val row = cursor.fetch()
                val map = row as Map<String, Any?>
                assertion(map)
            } finally {
                cursor.close()
            }
        } finally {
            plan.free()
        }
    }

    @Test
    fun testFeatureReading() {
        val session = NakshaSession.get()
        val builder = JbBuilder.create(1000)
        var feature = builder.buildFeatureFromMap(asMap(env.parse("""{
    "id": "Foo",
    "type": "Feature",
    "momType": "Topology",
    "properties": {
        "featureType": "Topology"
    }
}""")))
        assertEquals("Foo", session.getFeatureId(feature))
        assertEquals("Topology", session.getFeatureType(feature))

        feature = builder.buildFeatureFromMap(asMap(env.parse("""{
    "id": "Foo",
    "type": "Feature",
    "momType": "Topology",
    "properties": {
    }
}""")))
        assertEquals("Topology", session.getFeatureType(feature))

        feature = builder.buildFeatureFromMap(asMap(env.parse("""{
    "id": "Foo",
    "type": "FeatureX",
    "properties": {
    }
}""")))
        assertEquals("FeatureX", session.getFeatureType(feature))

        feature = builder.buildFeatureFromMap(asMap(env.parse("""{
    "id": "Foo",
    "properties": {
    }
}""")))
        assertEquals("Feature", session.getFeatureType(feature))
    }

    @Test
    fun testPartitioning() {
        assertEquals(2, Static.partitionNumber("XD44QgPaxbii"))
        assertEquals("2", Static.partitionNameForId("XD44QgPaxbii"))
    }
}