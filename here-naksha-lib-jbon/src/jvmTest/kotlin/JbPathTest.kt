import com.here.naksha.lib.jbon.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JbPathTest : JbAbstractTest() {

    private val json = JbPathTest::class.java.getResource("/pathTest.json")!!.readText(StandardCharsets.UTF_8)
    private val bytea = run {
        val feature = env.parse(json)
        check(feature is IMap)
        val builder = JbSession.get().newBuilder()
        val bytes = builder.buildFeatureFromMap(feature)
        assertEquals(216, bytes.size)
        bytes
    }
    private val path = JbPath(JbDictManager())

    @Test
    fun testParsingJson() {
        val env = JvmEnv()
        val raw = env.parse(json)
        val root = assertInstanceOf(JvmMap::class.java, raw)
        val properties = assertInstanceOf(JvmMap::class.java, root["properties"])
        val array = assertInstanceOf(Array::class.java, properties["array"])
        assertEquals(2, array.size)
    }

    @Test
    fun shouldProperlyReadTypes() {
        assertTrue(path.getBool(bytea, "properties.bool", false)!!)
        assertEquals(123, path.getInt32(bytea, "properties.int32")!!)
        assertEquals(10.0f, path.getFloat32(bytea, "properties.float")!!)
        assertEquals(3.4028234663852886E42, path.getDouble(bytea, "properties.double")!!)
        assertEquals("any String", path.getString(bytea, "properties.string")!!)

        assertThrows(NotImplementedError::class.java) { path.getInt64(bytea, "properties.int64")!! }
        // Replace with below after implementing getInt64
        //  assertEquals(2147483647000L, getInt64(bytea, "properties.int64")!!)
    }

    @Test
    fun shouldUseAlternativeWhenPathNotExist() {
        // given
        val alternative = 999
        val invalidPath = "dummy.not.real.path"

        // when
        val result = path.getInt32(bytea, invalidPath, alternative)!!

        // then
        assertEquals(alternative, result)
    }

    @Test
    fun shouldUseAlternativeNullWhenPathNotExist() {
        // given
        val invalidPath = "dummy.not.real.path"

        // when
        val result = path.getInt32(bytea, invalidPath, null)

        // then
        assertNull(result)
    }

    @Test
    fun searchInArrayNotYetSupported() {
        // given
        val arrayPath = "properties.array[0].int32"

        // when
        val result = path.getInt32(bytea, arrayPath)

        // then
        assertNull(result)
    }

    @Test
    fun shouldReturnAlternativeIfValueUnderPathIsNotSimpleValue() {
        // given
        val arrayPath = "properties"

        // when
        val result = path.getInt32(bytea, arrayPath)

        // then
        assertNull(result)
    }
}