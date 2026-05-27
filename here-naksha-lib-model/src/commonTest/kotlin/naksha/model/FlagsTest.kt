package naksha.model

import naksha.model.FlagsBits.FlagsBitsCompanion.FEATURE_CLEAR
import naksha.model.FlagsBits.FlagsBitsCompanion.FEATURE_SHIFT
import kotlin.test.*

class FlagsTest {
    @Test
    fun shouldProperlySetDefaultValues() {
        val flags = Flags()
        assertEquals(FeatureEncoding.JBON, flags.featureEncoding())
    }

    @Test
    fun shouldProperlySetFeatureEncoding() {
        val flags = Flags(0).withFeatureEncoding(FeatureEncoding.JBON_GZIP)

        assertEquals(FeatureEncoding.JBON_GZIP, flags.featureEncoding())
        assertEquals(FeatureEncoding.JBON_GZIP, flags)
        assertEquals(0, flags and FEATURE_CLEAR)
    }

    @Test
    fun testMaxFeatureEncoding() {
        var flags = 0
        flags = flags.withFeatureEncoding(15 shl FEATURE_SHIFT)

        assertEquals(15 shl FEATURE_SHIFT, flags)
        assertEquals(15, flags.featureEncoding() shr FEATURE_SHIFT)
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
}
