package naksha.model

import naksha.model.FlagsBits.FlagsBits_C.ACTION_SHIFT
import naksha.model.FlagsBits.FlagsBits_C.FEATURE_CLEAR
import naksha.model.FlagsBits.FlagsBits_C.FEATURE_SHIFT
import naksha.model.FlagsBits.FlagsBits_C.GEO_CLEAR
import naksha.model.FlagsBits.FlagsBits_C.GEO_SHIFT
import naksha.model.FlagsBits.FlagsBits_C.OP_SHIFT
import naksha.model.FlagsBits.FlagsBits_C.TAGS_CLEAR
import naksha.model.FlagsBits.FlagsBits_C.TAGS_SHIFT
import kotlin.test.*

class FlagsTest {
    @Test
    fun shouldProperlySetDefaultValues() {
        // given
        val flags = Flags()

        // expect default values
        assertEquals(GeoEncoding.TWKB, flags.geoEncoding())
        assertEquals(FeatureEncoding.JBON, flags.featureEncoding())
        assertEquals(TagsEncoding.JBON, flags.tagsEncoding())
        assertEquals(Action.CREATED.intValue, flags.action())
    }

    @Test
    fun shouldProperlySetGeometryEncoding() {
        val flags = Flags().withGeoEncoding(GeoEncoding.EWKB)

        assertEquals(GeoEncoding.EWKB, flags.geoEncoding())
        assertEquals(GeoEncoding.EWKB, flags)
        assertEquals(0, flags and GEO_CLEAR)
    }

    @Test
    fun shouldProperlySetFeatureEncoding() {
        val flags = Flags(0).withFeatureEncoding(FeatureEncoding.JBON_GZIP)

        assertEquals(FeatureEncoding.JBON_GZIP, flags.featureEncoding())
        assertEquals(FeatureEncoding.JBON_GZIP, flags)
        assertEquals(0, flags and FEATURE_CLEAR)
    }

    @Test
    fun shouldProperlySetTagsEncoding() {
        val flags = Flags(0).withTagsEncoding(TagsEncoding.JBON_GZIP)

        assertEquals(TagsEncoding.JBON_GZIP, flags.tagsEncoding())
        assertEquals(TagsEncoding.JBON_GZIP, flags)
        assertEquals(0, flags and TAGS_CLEAR)
    }

    @Test
    fun testMaxFlagValues() {
        //given
        var flags = 0

        // when
        flags = flags.withGeoEncoding(15 shl GEO_SHIFT)
        flags = flags.withFeatureEncoding(15 shl FEATURE_SHIFT)
        flags = flags.withTagsEncoding(15 shl TAGS_SHIFT)
        flags = flags.withAction(3 shl ACTION_SHIFT)
        flags = flags.withOperation( 15 shl OP_SHIFT)

        // then
        val expectation = (15 shl GEO_SHIFT) or (15 shl FEATURE_SHIFT) or (15 shl TAGS_SHIFT) or (3 shl ACTION_SHIFT) or ( 15 shl OP_SHIFT)
        assertEquals(expectation, flags)
        assertEquals(15, flags.geoEncoding() shr GEO_SHIFT)
        assertEquals(15, flags.featureEncoding() shr FEATURE_SHIFT)
        assertEquals(15, flags.tagsEncoding() shr TAGS_SHIFT)
        assertEquals(3, flags.action() shr ACTION_SHIFT)
        assertEquals(15, flags.operation() shr OP_SHIFT)
    }

    @Test
    fun testGeometryGzip() {
        var flags: Flags = Flags().withGeoEncoding(GeoEncoding.GEO_JSON)
        assertFalse(flags.geoGzip())

        flags = flags.geoGzipOn()
        assertEquals(GeoEncoding.GEO_JSON_GZIP, flags.geoEncoding())
        assertTrue(flags.geoGzip())

        flags = flags.geoGzipOff()
        assertEquals(GeoEncoding.GEO_JSON, flags.geoEncoding())
        assertFalse(flags.geoGzip())
    }

    @Test
    fun testFeatureGzip() {
        var flags: Flags = Flags().withFeatureEncoding(FeatureEncoding.JSON)
        assertFalse(flags.featureGzip())

        flags = flags.featureGzipOn()
        assertEquals(FeatureEncoding.JSON_GZIP, flags.featureEncoding())
        assertTrue(flags.featureGzip())

        flags = flags.featureGzipOff()
        assertEquals(FeatureEncoding.JSON, flags.featureEncoding())
        assertFalse(flags.featureGzip())
    }

    @Test
    fun testTagsGzip() {
        var flags: Flags = Flags().withTagsEncoding(TagsEncoding.JSON)
        assertFalse(flags.tagsGzip())

        flags = flags.tagsGzipOn()
        assertEquals(TagsEncoding.JSON_GZIP, flags.tagsEncoding())
        assertTrue(flags.tagsGzip())

        flags = flags.tagsGzipOff()
        assertEquals(TagsEncoding.JSON, flags.tagsEncoding())
        assertFalse(flags.tagsGzip())
    }
}