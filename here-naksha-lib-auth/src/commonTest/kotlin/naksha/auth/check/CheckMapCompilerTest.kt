package naksha.auth.check

import naksha.auth.*
import naksha.base.Platform.PlatformCompanion.forKClass
import naksha.base.Platform.PlatformCompanion.fromJSON
import kotlin.test.*

class CheckMapCompilerTest {

    @Test
    fun shouldGetStringChecks() {
        // Given:
        // A virtual operation 'demoOp', that accepts one parameter named 'foo', and we want the user
        // to only have the right to execute the operation, when the 'foo' parameter starts with 'prefix-',
        // ends with '-suffix' or is exactly 'strict'
        val userRights = assertNotNull(fromJSON("""
{
  "demoOp": [
    { "foo": "prefix-*", "bar": "*-suffix", "xyz": "strict" }
  ]
}""", forKClass(UserRights::class)))

        // Then:
        val filterList = assertNotNull(userRights["demoOp"])
        assertEquals(1, filterList.size)
        val filter = assertNotNull(filterList[0])
        assertEquals(3, filter.size)

        // And
        assertIs<StartsWith>(filter["foo"]).apply {
            assertNull(allOf)
            val anyOf = assertNotNull(this.anyOf)
            assertEquals(1, anyOf.size)
            assertEquals("prefix-", anyOf[0])
        }

        // And
        assertIs<EndsWith>(filter["bar"]).apply {
            assertNull(allOf)
            val anyOf = assertNotNull(this.anyOf)
            assertEquals(1, anyOf.size)
            assertEquals("-suffix", anyOf[0])
        }

        // And
        assertIs<Equals>(filter["xyz"]).apply {
            assertNull(allOf)
            val anyOf = assertNotNull(this.anyOf)
            assertEquals(1, anyOf.size)
            assertEquals("strict", anyOf[0])
        }
    }

    @Test
    fun shouldGetEqualsAnyOfCheck() {
        // Given:
        // A virtual operation 'demoOp', that accepts one parameter named 'name', and we want that the user
        // only has rights to execute this operation, when the 'name' parameter is "foo", "bar", or "buzz"
        val userRights = assertNotNull(fromJSON("""
{
  "demoOp": [
    { "name": ["foo", "bar", "buzz"] }
  ]
}""", forKClass(UserRights::class)))

        // Then:
        val filterList = assertNotNull(userRights["demoOp"])
        assertEquals(1, filterList.size)
        val filter = assertNotNull(filterList[0])

        // And
        val name = filter["name"]
        assertIs<Equals>(name).apply {
            assertNull(allOf)
            val anyOf = assertNotNull(this.anyOf)
            assertEquals(3, anyOf.size)
            assertEquals("foo", anyOf[0])
            assertEquals("bar", anyOf[1])
            assertEquals("buzz", anyOf[2])
        }
    }

    @Test
    fun shouldReturnUndefinedCheckForUnknownValue() {
        // Given:
        // A virtual operation 'demoOp', that accepts one parameter named 'name', and we want that the user
        // only has rights to execute this operation, when the 'name' parameter matches a check, that does not exist
        val userRights = assertNotNull(fromJSON("""
{
  "demoOp": [
    { "name": {} }
  ]
}""", forKClass(UserRights::class)))

        // Then:
        val filterList = assertNotNull(userRights["demoOp"])
        assertEquals(1, filterList.size)
        val filter = assertNotNull(filterList[0])

        // And
        assertIs<Check>(filter["name"]).apply {
            assertNull(allOf)
            assertNull(anyOf)
            assertEquals(CheckOp.UNDEFINED, op)
        }
    }
}