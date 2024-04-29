import com.here.naksha.lib.nak.Flags
import com.here.naksha.lib.nak.Flags.Companion.FEATURE_ENCODING_JBON
import com.here.naksha.lib.nak.Flags.Companion.FEATURE_ENCODING_JBON_GZIP
import com.here.naksha.lib.nak.Flags.Companion.FEATURE_ENCODING_JSON
import com.here.naksha.lib.nak.Flags.Companion.FEATURE_ENCODING_JSON_GZIP
import com.here.naksha.lib.nak.Flags.Companion.GEO_TYPE_EWKB
import com.here.naksha.lib.nak.Flags.Companion.GEO_TYPE_NULL
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

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
        assertThrows(Exception::class.java) { flags.setGeometryEncoding(64) }
        assertThrows(Exception::class.java) { flags.setFeatureEncoding(64) }
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