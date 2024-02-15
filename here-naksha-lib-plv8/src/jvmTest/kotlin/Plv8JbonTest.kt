import com.here.naksha.lib.jbon.*
import com.here.naksha.lib.plv8.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@Suppress("UNCHECKED_CAST")
class Plv8JbonTest : Plv8TestContainer() {

    private fun jsonToJbonByteArray(json:String) : ByteArray {
        val builder = JbBuilder(newDataView(16384))
        builder.reset()
        val raw = env.parse(json)
        return builder.buildFeatureFromMap(raw as IMap)
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
        val featureBytea = jsonToJbonByteArray("""{"i":123}""");
        val plan = session.sql.prepare(
                "SELECT jb_get_int4($1,$2,$3)",
                arrayOf(SQL_BYTE_ARRAY, SQL_STRING, SQL_INT32)
        )
        // when
        query(plan, arrayOf(featureBytea, "i", 0)) {
            // then
            assertEquals(123, it["jb_get_int4"])
        }
    }

    @Order(1)
    @Test
    fun testSql_jb_get_real() {
        // given
        val session = NakshaSession.get()
        val featureBytea = jsonToJbonByteArray("""{"x":0.5}""");
        val plan = session.sql.prepare(
                "SELECT jb_get_real($1,$2,$3)",
                arrayOf(SQL_BYTE_ARRAY, SQL_STRING, SQL_FLOAT32)
        )
        // when
        query(plan, arrayOf(featureBytea, "x", 0.0f)) {
            // then
            assertEquals(0.5f, it["jb_get_real"])
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

    @Test
    fun testInsertJbonToTable() {
        val session = NakshaSession.get()
    val view = env.newDataView(ByteArray(1024))
    val builder = XyzBuilder(view)
    val xyzOp = builder.buildXyzOp(XYZ_OP_CREATE, "ID_1", "uuid", "crid")
    val featureBytea = jsonToJbonByteArray("""{"id":"ID_1","properties":{"@ns:com:here:xyz":{"uuid":"asdf"}}}""");
    val wkb = byteArrayOf(1, 1, 0, 0, -96, -26, 16, 0, 0, 0, 0, 0, 0, 0, 0, 20, 64, 0, 0, 0, 0, 0, 0, 24, 64, 0, 0, 0, 0, 0, 0, 0, 64)
        session.sql.execute("CREATE TABLE IF NOT EXISTS feature_jbon(jsondata bytea, geo GEOMETRY(GeometryZ, 4326), i serial PRIMARY KEY NOT NULL) ;")

        (session.sql as Plv8Sql).conn!!.commit()
        val plan = session.sql.prepare(
                "SELECT op, id, xyz, tags, ST_AsEWKB(geo), feature, err_no, err_msg from naksha_write_features($1,$2,$3,$4,$5)",
                arrayOf(SQL_STRING, SQL_BYTEA_ARRAY, SQL_GEOMETRY_ARRAY, SQL_BYTEA_ARRAY, SQL_BYTEA_ARRAY)
        )

        // when
        query(plan, arrayOf("feature_jbon", arrayOf(xyzOp), arrayOf(wkb), arrayOf(featureBytea), arrayOf(byteArrayOf()),)) {
            // then
            assertEquals("CREATED", it["op"])
            assertEquals("ID_1", it["id"])
            val uuidInFeature = JbPath.getString(it["feature"] as ByteArray, "properties.@ns:com:here:xyz.uuid")
            assertEquals(uuidInFeature, it["uuid"])
        }

        // verify
        val result = session.sql.execute("select jsondata from feature_jbon ;") as Array<Map<String, Any>>
        assertArrayEquals(featureBytea, result[0]["jsondata"] as ByteArray)
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
}