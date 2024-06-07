package naksha.base

import naksha.base.Flags.Companion.FEATURE_ENCODING_JBON
import naksha.base.Flags.Companion.FEATURE_ENCODING_JBON_GZIP
import naksha.base.Flags.Companion.FEATURE_ENCODING_JSON
import naksha.base.Flags.Companion.FEATURE_ENCODING_JSON_GZIP
import naksha.base.Flags.Companion.GEO_TYPE_EWKB
import naksha.base.Flags.Companion.GEO_TYPE_NULL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FlagsTest {

    @Test
    fun shouldProperlySetDefaultValues() {
        // given
        val flags = Flags() // empty flags

        // expect default values
        assertEquals(GEO_TYPE_NULL, flags.getGeometryEncoding())
        assertEquals(FEATURE_ENCODING_JBON, flags.getFeatureEncoding())
    }

    @Test
    fun shouldProperlySetGeometryEncoding() {
        // given
        val flags = Flags()

        // when
        flags.setGeometryEncoding(GEO_TYPE_EWKB)

        // then
        assertEquals(GEO_TYPE_EWKB, flags.getGeometryEncoding())
        // untouched other flags
        assertEquals(FEATURE_ENCODING_JBON, flags.getFeatureEncoding())
        assertEquals(0, flags.getReserved())
    }

    @Test
    fun shouldProperlySetFeatureEncoding() {
        // given
        val flags = Flags()

        // when
        flags.setFeatureEncoding(FEATURE_ENCODING_JSON_GZIP)

        // then
        assertEquals(FEATURE_ENCODING_JSON_GZIP, flags.getFeatureEncoding())
        // untouched other flags
        assertEquals(Flags.GEO_TYPE_NULL, flags.getGeometryEncoding())
        assertEquals(0, flags.getReserved())
    }

    @Test
    fun maxGeometryEncodingShouldToModifyFeatureEncoding() {
        // given
        val flags = Flags()

        // when
        flags.setGeometryEncoding(63)

        // then
        assertEquals(63, flags.getGeometryEncoding())
        assertEquals(FEATURE_ENCODING_JBON, flags.getFeatureEncoding())
    }

    @Test
    fun maxFeatureEncodingShouldToModifyGeometryEncoding() {
        // given
        val flags = Flags()

        // when
        flags.setGeometryEncoding(0)
        flags.setFeatureEncoding(63)

        // then
        assertEquals(0, flags.getGeometryEncoding())
        assertEquals(63, flags.getFeatureEncoding())
        assertEquals(0, flags.getReserved())
    }

    @Test
    fun shouldNotBeAbleToExceedValues() {
        // given
        val flags = Flags()

        // when
        assertFailsWith<Exception> { flags.setGeometryEncoding(64) }
        assertFailsWith<Exception> { flags.setFeatureEncoding(64) }
    }

    @Test
    fun testCombinedFlag() {
        // given
        val flags = Flags()

        // when
        flags.setGeometryEncoding(11)
        flags.setFeatureEncoding(15)

        val restoredFlags = Flags(flags.toCombinedFlags())

        // then
        assertEquals(11, restoredFlags.getGeometryEncoding())
        assertEquals(15, restoredFlags.getFeatureEncoding())
    }

    @Test
    fun testEnforceGzipCompression() {
        // given
        val flags = Flags()

        // when
        flags.setFeatureEncoding(FEATURE_ENCODING_JSON)
        flags.forceGzipOnFeatureEncoding()

        // then
        assertEquals(FEATURE_ENCODING_JSON_GZIP, flags.getFeatureEncoding())

        // when
        flags.setFeatureEncoding(FEATURE_ENCODING_JBON)
        flags.forceGzipOnFeatureEncoding()

        // then
        assertEquals(FEATURE_ENCODING_JBON_GZIP, flags.getFeatureEncoding())
    }
}