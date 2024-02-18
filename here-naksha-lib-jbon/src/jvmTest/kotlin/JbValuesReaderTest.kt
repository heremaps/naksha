import com.here.naksha.lib.jbon.*
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class JbValuesReaderTest : JbAbstractTest() {

    @Test
    fun shouldReadTopElements() {
        // given
        val json = """{
            "type": "Feature"
        }""".trimIndent()
        val bytea = jsonToJbonByte(json)
        val jbMap = byteToJbMap(bytea)

        // when
        val imap = jbMap.toIMap()

        // then
        assertEquals("Feature", imap["type"])
    }

    @Test
    fun shouldReadSubProperties() {
        // given
        val json = """{
           "properties": {
             "bool": true,
             "int32": 123,
             "int64": 2147483647000,
             "float": 10.0,
             "double": 3402823466385288600000000000000000000001111.000000,
             "string": "any String",
             "null": null
          }
        }""".trimIndent()
        val bytea = jsonToJbonByte(json)
        val jbMap = byteToJbMap(bytea)

        // when
        val imap = jbMap.toIMap()

        // then
        val properties: Map<String, *> = imap["properties"]!!
        assertEquals(true, properties["bool"])
        assertEquals("any String", properties["string"])
        assertNull(properties["null"])
        assertEquals(3402823466385288600000000000000000000001111.000000, properties["double"])
        assertEquals(2147483647000L, (properties["int64"] as BigInt64).toLong())
        assertEquals(123, properties["int32"])
        assertEquals(10.0f, properties["float"])
    }

    @Test
    fun shouldReadPropertyWithArray() {
        // given
        val json = """{
           "properties": {
             "array": [
               { "int32": 1 },
               null,
               { "int32": 2 }
             ]
          }
        }""".trimIndent()
        val bytea = jsonToJbonByte(json)
        val jbMap = byteToJbMap(bytea)

        // when
        val imap = jbMap.toIMap()

        // then
        val properties: Map<String, *> = imap["properties"]!!
        val array = properties["array"] as Array<Any?>
        assertEquals(1, (array[0] as IMap)["int32"])
        assertNull(array[1])
        assertEquals(2, (array[2] as IMap)["int32"])
    }

    private fun byteToJbMap(bytea: ByteArray): JbMap {
        val feature = JbFeature().mapBytes(bytea)
        val reader = feature.reader
        val jmap = JbMap()
        jmap.mapReader(reader)
        return jmap
    }

    private fun jsonToJbonByte(json: String): ByteArray {
        val feature = env.parse(json)
        val builder = JbSession.get().newBuilder()
        return builder.buildFeatureFromMap(feature as IMap)
    }
}