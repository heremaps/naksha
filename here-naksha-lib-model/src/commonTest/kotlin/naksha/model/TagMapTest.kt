package naksha.model

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

    object NotSupportedType
}