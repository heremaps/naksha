package naksha.model

import naksha.model.TagNormalizer.TagNormalizer_C.joinTag
import naksha.model.TagNormalizer.TagNormalizer_C.normalizeTag
import naksha.model.TagNormalizer.TagNormalizer_C.splitTag
import kotlin.test.Test
import kotlin.test.assertEquals

class TagNormalizerTest {

    @Test
    fun shouldRemoveNonAscii() {
        val tagsToBeClearedFromAscii = mapOf(
            "p®¡©e=100£" to "pe=100",                  // regular tag
            "~twice_¼=single_½" to "~twice_=single_",  // starting with '~'
            "#some=tµ¶ag" to "#some=tag",              // starting with '#'
        )

        tagsToBeClearedFromAscii.forEach { (before, after) ->
            assertEquals(after, TagNormalizer.normalizeTag(before))
        }
    }

    @Test
    fun shouldLeaveNonAsciiAsIs() {
        val tagsWithAsciiToBePreserved = listOf(
            "@p®¡©e=100£",         // starting with '@'
            "ref_p®¡©e=100£",      // starting with 'ref_'
            "sourceID_p®¡©e=100£",  // starting with 'sourceID'
        )

        tagsWithAsciiToBePreserved.forEach { tag ->
            assertEquals(tag, TagNormalizer.normalizeTag(tag))
        }
    }

    @Test
    fun shouldLowercase() {
        val tag = "Some_Tag:=1235"
        assertEquals(tag.lowercase(), TagNormalizer.normalizeTag(tag))
    }

    @Test
    fun shouldNotLowercase() {
        val tagsNotToBeLowercased = listOf(
            "@Some_Tag:=1235",
            "ref_Some_Tag:=1235",
            "~Some_Tag:=1235",
            "#Some_Tag:=1235",
            "sourceID_Some_Tag:=1235"
        )

        tagsNotToBeLowercased.forEach { tag ->
            assertEquals(tag, TagNormalizer.normalizeTag(tag))
        }
    }

    @Test
    fun shouldSplit() {
        val tagsToBeSplit = listOf(
            "@some_tag:=1235",
            "~some_tag:=1235",
            "#some_tag:=1235",
            "some_tag:=1235"
        )

        tagsToBeSplit.forEach { rawTag ->
            val expectedKey = rawTag.split(":")[0]
            val normalized = normalizeTag(rawTag)
            val (tagKey, tagValue) = splitTag(normalized)

            assertEquals(expectedKey, tagKey)
            assertEquals(1235.0, tagValue)
        }

        run {
            val (key, value) = splitTag("some_tag:==1235")
            assertEquals("some_tag:=", key)
            assertEquals("1235", value)
        }
        run {
            val (key, value) = splitTag("some_tag==:=1235")
            assertEquals("some_tag==", key)
            assertEquals(1235.0, value)
        }
    }

    @Test
    fun shouldNotSplit() {
        val tagsNotToBeSplit = listOf(
            "ref_some_tag:=1235",
            "sourceID_some_tag:=1235"
        )

        tagsNotToBeSplit.forEach { rawTag ->
            val normalized = TagNormalizer.normalizeTag(rawTag)
            val (tagKey, tagValue) = splitTag(normalized)

            assertEquals(rawTag, tagKey)
            assertEquals(null, tagValue)
        }
    }

    @Test
    fun shouldSplitSingleCharTags() {
        // Given
        val tagToBeSplit = "a=b"

        // When:
        val normalized = TagNormalizer.normalizeTag(tagToBeSplit)

       // And:
        val (key, value) = splitTag(normalized)

        // Then
        assertEquals("a", key)
        assertEquals("b", value)
    }

    @Test
    fun shouldJoin() {
        assertEquals("foo", joinTag("foo", null))
        assertEquals("foobar:=", joinTag("foobar:=", null))
        assertEquals("foo=bar=test", joinTag("foo=bar", "test"))
        assertEquals("foobar:=:=1234.0", joinTag("foobar:=", 1234.0))
        assertEquals("foo==bar", joinTag("foo==bar", null))
        assertEquals("foo:=1235.0", joinTag("foo", 1235))
        assertEquals("foo=1235", joinTag("foo", "1235"))
        assertEquals("foo:=1230.0", joinTag("foo", 1230))
        assertEquals("foo:=1234.9", joinTag("foo", 1234.9))
        assertEquals("foo:=true", joinTag("foo", true))
        assertEquals("foo=true", joinTag("foo", "true"))
    }
}
