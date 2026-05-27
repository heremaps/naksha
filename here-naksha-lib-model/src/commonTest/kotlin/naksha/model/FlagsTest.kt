package naksha.model

import naksha.model.FlagsBits.FlagsBitsCompanion.ACTION_SHIFT
import naksha.model.FlagsBits.FlagsBitsCompanion.FEATURE_CLEAR
import naksha.model.FlagsBits.FlagsBitsCompanion.FEATURE_SHIFT
import naksha.model.FlagsBits.FlagsBitsCompanion.OP_SHIFT
import kotlin.test.*

class FlagsTest {
    @Test
    fun shouldProperlySetDefaultValues() {
        val flags = Flags()
        assertEquals(FeatureEncoding.JBON, flags.featureEncoding())
        assertEquals(Action.CREATED.intValue, flags.action())
    }

    @Test
    fun shouldProperlySetFeatureEncoding() {
        val flags = Flags(0).withFeatureEncoding(FeatureEncoding.JBON_GZIP)

        assertEquals(FeatureEncoding.JBON_GZIP, flags.featureEncoding())
        assertEquals(FeatureEncoding.JBON_GZIP, flags)
        assertEquals(0, flags and FEATURE_CLEAR)
    }

    @Test
    fun testMaxFlagValues() {
        var flags = 0

        flags = flags.withFeatureEncoding(15 shl FEATURE_SHIFT)
        flags = flags.withAction(3 shl ACTION_SHIFT)
        flags = flags.withOperation(15 shl OP_SHIFT)

        val expectation = (15 shl FEATURE_SHIFT) or (3 shl ACTION_SHIFT) or (15 shl OP_SHIFT)
        assertEquals(expectation, flags)
        assertEquals(15, flags.featureEncoding() shr FEATURE_SHIFT)
        assertEquals(3, flags.action() shr ACTION_SHIFT)
        assertEquals(15, flags.operation() shr OP_SHIFT)
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
