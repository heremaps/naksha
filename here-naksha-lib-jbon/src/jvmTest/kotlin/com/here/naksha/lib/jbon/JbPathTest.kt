package com.here.naksha.lib.jbon

import com.here.naksha.lib.jbon.JbPath.Companion.getBool
import com.here.naksha.lib.jbon.JbPath.Companion.getDouble
import com.here.naksha.lib.jbon.JbPath.Companion.getFloat32
import com.here.naksha.lib.jbon.JbPath.Companion.getInt32
import com.here.naksha.lib.jbon.JbPath.Companion.getInt64
import com.here.naksha.lib.jbon.JbPath.Companion.getString
import com.here.naksha.lib.jbon.JsonConverter.jsonToJbonByteArray
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JbPathTest {

    private val json = JbPathTest::class.java.getResource("/pathTest.json")!!.readText(StandardCharsets.UTF_8)
    private val bytea = jsonToJbonByteArray(json)

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
        val alternative = null
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

    companion object {
        @JvmStatic
        @BeforeAll
        fun init(): Unit {
            JvmSession.register()
        }
    }
}