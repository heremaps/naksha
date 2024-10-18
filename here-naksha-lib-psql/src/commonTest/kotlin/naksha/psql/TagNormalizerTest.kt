package naksha.psql

import naksha.model.TagNormalizer
import kotlin.test.Test
import kotlin.test.assertEquals

class TagNormalizerTest {

    @Test
    fun shouldRemoveNonAscii(){
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
    fun shouldLeaveNonAsciiAsIs(){
        val tagsWithAsciiToBePreserved = listOf(
            "@p®¡©e=100£",         // starting with '@'
            "ref_p®¡©e=100£",      // starting with 'ref_'
            "sourceIDp®¡©e=100£",  // starting with 'sourceID'
        )

        tagsWithAsciiToBePreserved.forEach { tag ->
            assertEquals(tag, TagNormalizer.normalizeTag(tag))
        }
    }

    @Test
    fun shouldLowercase(){
        val tag = "Some_Tag:=1235"
        assertEquals(tag.lowercase(), TagNormalizer.normalizeTag(tag))
    }

    @Test
    fun shouldNotLowercase(){
        val tagsNotToBeLowercased = listOf(
            "@Some_Tag:=1235",
            "ref_Some_Tag:=1235",
            "~Some_Tag:=1235",
            "#Some_Tag:=1235",
            "sourceIDSome_Tag:=1235"
        )

        tagsNotToBeLowercased.forEach { tag ->
            assertEquals(tag, TagNormalizer.normalizeTag(tag))
        }
    }

    @Test
    fun shouldSplit(){
        val tagsToBeSplit = listOf(
            "@some_tag:=1235",
            "~some_tag:=1235",
            "#some_tag:=1235",
            "some_tag:=1235"
        )

        tagsToBeSplit.forEach { rawTag ->
            val expectedKey = rawTag.split(":")[0]
            val normalized = TagNormalizer.normalizeTag(rawTag)
            val tag = TagNormalizer.splitNormalizedTag(normalized)

            assertEquals(expectedKey, tag.key)
            assertEquals(1235.0, tag.value)
            assertEquals(rawTag, tag.tag)
        }
    }

    @Test
    fun shouldNotSplit(){
        val tagsNotToBeSplit = listOf(
            "ref_some_tag:=1235",
            "sourceIDsome_tag:=1235"
        )

        tagsNotToBeSplit.forEach { rawTag ->
            val normalized = TagNormalizer.normalizeTag(rawTag)
            val tag = TagNormalizer.splitNormalizedTag(normalized)

            assertEquals(rawTag, tag.key)
            assertEquals(null, tag.value)
            assertEquals(rawTag, tag.tag)
        }
    }
}