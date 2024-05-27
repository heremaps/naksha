import com.here.naksha.lib.naksha.Flags
import com.here.naksha.lib.naksha.Flags.FEATURE_ENCODING_JBON
import com.here.naksha.lib.naksha.Flags.FEATURE_ENCODING_JBON_GZIP
import com.here.naksha.lib.naksha.Flags.FEATURE_ENCODING_JSON
import com.here.naksha.lib.naksha.Flags.FEATURE_ENCODING_JSON_GZIP
import com.here.naksha.lib.naksha.Flags.GEOMETRY_FLAG_ENCODER
import com.here.naksha.lib.naksha.Flags.GEO_TYPE_EWKB
import com.here.naksha.lib.naksha.Flags.GEO_TYPE_NULL
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FlagsTest {

    @Test
    fun shouldProperlySetDefaultValues() {
        // given
        var flags = Flags.encodeGeometryFlag(0, Flags.DEFAULT_GEOMETRY_ENCODING)
        flags = Flags.encodeFeatureFlag(flags, Flags.DEFAULT_FEATURE_ENCODING)

        // expect default values
        assertEquals(GEO_TYPE_NULL, Flags.readGeometryEncoding(flags))
        assertEquals(FEATURE_ENCODING_JBON, Flags.readFeatureEncoding(flags))
    }

    @Test
    fun shouldProperlySetGeometryEncoding() {
        // given
        var flags = Flags.encodeFeatureFlag(0, Flags.DEFAULT_FEATURE_ENCODING)

        // when
        flags = GEOMETRY_FLAG_ENCODER.encodeNew(flags, GEO_TYPE_EWKB)

        // then
        assertEquals(GEO_TYPE_EWKB, Flags.readGeometryEncoding(flags))
        // untouched other flags
        assertEquals(FEATURE_ENCODING_JBON, Flags.readFeatureEncoding(flags))
        assertEquals(0, Flags.readAction(flags))
    }

    @Test
    fun shouldProperlySetFeatureEncoding() {
        // given
        var flags = 0

        // when
        flags = Flags.encodeFeatureFlag(flags, FEATURE_ENCODING_JSON_GZIP)

        // then
        assertEquals(FEATURE_ENCODING_JSON_GZIP, Flags.readFeatureEncoding(flags))
        // untouched other flags
        assertEquals(0, Flags.readGeometryEncoding(flags))
    }

    @Test
    fun maxGeometryEncodingShouldNotModifyFeatureEncoding() {
        // given
        var flags = 0

        // when
        flags = GEOMETRY_FLAG_ENCODER.encodeNew(flags, 15)

        // then
        assertEquals(15, Flags.readGeometryEncoding(flags))
        assertEquals(0, Flags.readFeatureEncoding(flags))
    }

    @Test
    fun maxFeatureEncodingShouldNotModifyGeometryEncoding() {
        // given
        var flags = 0

        // when
        flags = Flags.encodeFeatureFlag(flags, 15)

        // then
        assertEquals(15, Flags.readFeatureEncoding(flags))
        assertEquals(0, Flags.readGeometryEncoding(flags))
    }

    @Test
    fun shouldNotBeAbleToExceedValues() {
        assertThrows(Exception::class.java) { Flags.encodeFeatureFlag(0, 16) }
        assertThrows(Exception::class.java) { Flags.encodeGeometryFlag(0, 16) }
        assertThrows(Exception::class.java) { Flags.encodeAction(0, 4) }
        assertThrows(Exception::class.java) { Flags.encodeTagsFlag(0, 16) }
    }

    @Test
    fun testMaxFlagValues() {
        //given
        var flags = 0

        // when
        flags = Flags.encodeFeatureFlag(flags, 15)
        flags = Flags.encodeGeometryFlag(flags, 15)
        flags = Flags.encodeTagsFlag(flags, 15)
        flags = Flags.encodeAction(flags, 3)

        // then
        assertEquals(16383, flags)
        assertEquals(15, Flags.readFeatureEncoding(flags))
        assertEquals(15, Flags.readGeometryEncoding(flags))
        assertEquals(15, Flags.readTagsEncoding(flags))
        assertEquals(3, Flags.readAction(flags))

    }

    @Test
    fun testEnforceGzipCompression() {
        // given
        var flags = 0

        // when
        flags = Flags.encodeFeatureFlag(flags, FEATURE_ENCODING_JSON)
        flags = Flags.forceGzipOnFeatureEncoding(flags)

        // then
        assertEquals(FEATURE_ENCODING_JSON_GZIP, Flags.readFeatureEncoding(flags))

        // when
        flags = Flags.encodeFeatureFlag(flags, FEATURE_ENCODING_JBON)
        flags = Flags.forceGzipOnFeatureEncoding(flags)

        // then
        assertEquals(FEATURE_ENCODING_JBON_GZIP, Flags.readFeatureEncoding(flags))
        assertTrue(Flags.isFeatureEncodedWithGZip(flags))

        // when
        flags = Flags.turnOffGzipOnFeatureEncoding(flags)

        assertEquals(FEATURE_ENCODING_JBON, Flags.readFeatureEncoding(flags))
        assertFalse(Flags.isFeatureEncodedWithGZip(flags))
    }
}