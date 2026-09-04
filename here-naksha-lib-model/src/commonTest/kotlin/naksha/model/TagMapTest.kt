package naksha.model

import naksha.base.NakshaException
import kotlin.test.*

class TagMapTest {

    @Test
    fun shouldBeConvertedFromTagList(){
        // Given:
        val tagList = TagList("foo=bar", "no-value", "flag:=true")

        // When:
        val tagMap = TagMap(tagList)

        // Then
        assertEquals(3, tagList.size)
        assertEquals("bar", tagMap["foo"])
        assertTrue(tagMap.contains("no-value"))
        assertNull(tagMap["no-value"])
        assertEquals(true, tagMap["flag"])
    }

    @Test
    fun shouldBeConvertedToTagList(){
        // Given:
        val tagMap = TagMap().apply {
            put("foo", "bar")
            put("no-value", null)
            put("flag", true)
        }

        // When:
        val tagList = tagMap.toTagList()

        // Then
        assertTrue(tagList.containsAll(listOf("foo=bar", "no-value", "flag:=true")))
    }

    @Test
    fun shouldFailWhenConvertingToListWithUnsupportedType(){
        // Given:
        val tagMap = TagMap().apply {
            put("foo", "bar")
            put("failure-reason", NotSupportedType)
        }

        // When:
        val failure = assertFails {
            tagMap.toTagList()
        }

        // Then:
        assertIs<NakshaException>(failure)
        assertEquals("Tag values can only be String, Boolean or Number", failure.message)
    }

    @Test
    fun shouldAllowToSplitReferences() {
        val tagList = TagList("@sourceId=Ref\$1")
        assertEquals(1, tagList.size)
        val tagMap = tagList.toTagMap()
        assertEquals(1, tagMap.size)
        assertTrue(tagMap.containsKey("@sourceId"))
        assertEquals("Ref\$1", tagMap["@sourceId"])
        assertEquals("@sourceId", tagMap.keys.first())
        assertEquals("Ref\$1", tagMap.values.first())
    }

    @Test
    fun shouldSupportSourceID() {
        val tagList = TagList("sourceID_Ref\$1")
        assertEquals(1, tagList.size)
        val tagMap = tagList.toTagMap()
        assertEquals(1, tagMap.size)
        assertTrue(tagMap.containsKey("sourceID_Ref\$1"))
        assertNull(tagMap["sourceID_Ref\$1"])
        assertEquals("sourceID_Ref\$1", tagMap.keys.first())
        assertNull(tagMap.values.first())
    }

    object NotSupportedType
}