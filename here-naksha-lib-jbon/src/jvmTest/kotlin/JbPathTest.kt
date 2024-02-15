import com.here.naksha.lib.jbon.JbPath.Companion.getBool
import com.here.naksha.lib.jbon.JbPath.Companion.getDouble
import com.here.naksha.lib.jbon.JbPath.Companion.getFloat32
import com.here.naksha.lib.jbon.JbPath.Companion.getInt32
import com.here.naksha.lib.jbon.JbPath.Companion.getInt64
import com.here.naksha.lib.jbon.JbPath.Companion.getString
import com.here.naksha.lib.jbon.IMap
import com.here.naksha.lib.jbon.JbSession
import com.here.naksha.lib.jbon.JvmEnv
import com.here.naksha.lib.jbon.JvmMap
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
        assertEquals(223, bytes.size)
        bytes
    }

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
        assertTrue(getBool(bytea, "properties.bool", false)!!)
        assertEquals(123, getInt32(bytea, "properties.int32")!!)
        assertEquals(10.0f, getFloat32(bytea, "properties.float")!!)
        assertEquals(3.4028234663852886E42, getDouble(bytea, "properties.double")!!)
        assertEquals("any String", getString(bytea, "properties.string")!!)

        assertThrows(NotImplementedError::class.java) { getInt64(bytea, "properties.int64")!! }
        // Replace with below after implementing getInt64
        //  assertEquals(2147483647000L, getInt64(bytea, "properties.int64")!!)
    }

    @Test
    fun shouldUseAlternativeWhenPathNotExist() {
        // given
        val alternative = 999
        val invalidPath = "dummy.not.real.path"

        // when
        val result = getInt32(bytea, invalidPath, alternative)!!

        // then
        assertEquals(alternative, result)
    }

    @Test
    fun shouldUseAlternativeNullWhenPathNotExist() {
        // given
        val invalidPath = "dummy.not.real.path"

        // when
        val result = getInt32(bytea, invalidPath, null)

        // then
        assertNull(result)
    }

    @Test
    fun searchInArrayNotYetSupported() {
        // given
        val arrayPath = "properties.array[0].int32"

        // when
        val result = getInt32(bytea, arrayPath)

        // then
        assertNull(result)
    }

    @Test
    fun shouldReturnAlternativeIfValueUnderPathIsNotSimpleValue() {
        // given
        val arrayPath = "properties"

        // when
        val result = getInt32(bytea, arrayPath)

        // then
        assertNull(result)
    }
}