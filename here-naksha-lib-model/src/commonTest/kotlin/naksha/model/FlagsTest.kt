package naksha.model

import naksha.model.FlagsBits.Companion.ACTION_SHIFT
import naksha.model.FlagsBits.Companion.FEATURE_CLEAR
import naksha.model.FlagsBits.Companion.FEATURE_SHIFT
import naksha.model.FlagsBits.Companion.GEO_CLEAR
import naksha.model.FlagsBits.Companion.GEO_SHIFT
import naksha.model.FlagsBits.Companion.TAGS_CLEAR
import naksha.model.FlagsBits.Companion.TAGS_SHIFT
import kotlin.test.*

class FlagsTest {
    @Test
    fun shouldProperlySetDefaultValues() {
        // given
        val flags = Flags()

        // expect default values
        assertEquals(GeoEncoding.TWKB, flags.geoEncoding())
        assertEquals(FeatureEncoding.JBON, flags.featureEncoding())
        assertEquals(TagsEncoding.JSON, flags.tagsEncoding())
        assertEquals(Action.CREATED, flags.action())
    }

    @Test
    fun shouldProperlySetGeometryEncoding() {
        val flags = Flags().geoEncoding(GeoEncoding.EWKB)

        assertEquals(GeoEncoding.EWKB, flags.geoEncoding())
        assertEquals(GeoEncoding.EWKB, flags)
        assertEquals(0, flags and GEO_CLEAR)
    }

    @Test
    fun shouldProperlySetFeatureEncoding() {
        val flags = Flags(0).featureEncoding(FeatureEncoding.JBON_GZIP)

        assertEquals(FeatureEncoding.JBON_GZIP, flags.featureEncoding())
        assertEquals(FeatureEncoding.JBON_GZIP, flags)
        assertEquals(0, flags and FEATURE_CLEAR)
    }

    @Test
    fun shouldProperlySetTagsEncoding() {
        val flags = Flags(0).tagsEncoding(TagsEncoding.JBON_GZIP)

        assertEquals(TagsEncoding.JBON_GZIP, flags.tagsEncoding())
        assertEquals(TagsEncoding.JBON_GZIP, flags)
        assertEquals(0, flags and TAGS_CLEAR)
    }

    @Test
    fun testMaxFlagValues() {
        //given
        var flags = 0

        // when
        flags = flags.geoEncoding(15 shl GEO_SHIFT)
        flags = flags.featureEncoding(15 shl FEATURE_SHIFT)
        flags = flags.tagsEncoding(15 shl TAGS_SHIFT)
        flags = flags.action(3 shl ACTION_SHIFT)

        // then
        assertEquals(16383, flags)
        assertEquals(15, flags.geoEncoding() shr GEO_SHIFT)
        assertEquals(15, flags.featureEncoding() shr FEATURE_SHIFT)
        assertEquals(15, flags.tagsEncoding() shr TAGS_SHIFT)
        assertEquals(3, flags.action() shr ACTION_SHIFT)
    }

    @Test
    fun testGeometryGzip() {
        var flags: Flags = Flags().geoEncoding(GeoEncoding.GEO_JSON)
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
        var flags: Flags = Flags().featureEncoding(FeatureEncoding.JSON)
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
        var flags: Flags = Flags().tagsEncoding(TagsEncoding.JSON)
        assertFalse(flags.tagsGzip())

        flags = flags.tagsGzipOn()
        assertEquals(TagsEncoding.JSON_GZIP, flags.tagsEncoding())
        assertTrue(flags.tagsGzip())

        flags = flags.tagsGzipOff()
        assertEquals(TagsEncoding.JSON, flags.tagsEncoding())
        assertFalse(flags.tagsGzip())
    }
}