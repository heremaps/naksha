package naksha.model

import naksha.model.XyzProxy.Companion.normalizeTag
import naksha.model.XyzProxy.Companion.normalizeTags
import kotlin.test.Test
import kotlin.test.assertEquals

class XyzProxyTest {

    @Test
    fun testTagNormalization() {
        // expect
        assertEquals("zara", normalizeTag("žara"))
        assertEquals("@žara", normalizeTag("@žara"))
        assertEquals("", normalizeTag(""))
        assertEquals("best", normalizeTag("BEST"))
        assertEquals("#Best", normalizeTag("#Best"))
        assertEquals("~Best", normalizeTag("~Best"))
        assertEquals("ref_Best", normalizeTag("ref_Best"))
        assertEquals("sourceID_Best", normalizeTag("sourceID_Best"))
        assertEquals("+best", normalizeTag("+Best"))
    }

    @Test
    fun testTagsNormalization() {
        // given
        val tags = TagsProxy("A", "B", "#C")

        // when
        normalizeTags(tags)

        // then
        assertEquals(listOf("a", "b", "#C"), tags.toList())
    }
}