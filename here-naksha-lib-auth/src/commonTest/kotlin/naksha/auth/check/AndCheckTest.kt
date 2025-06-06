package naksha.auth.check

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndCheckTest {

    @Test
    fun shouldPass() {
        // Given:
        // Pseudo URM that expects a parameter to
        val composedCheck = Check().apply {
            useAllOf().apply {
                add(Equals("foo"))
                add(StartsWith("bar")) // bar*
                add(EndsWith("buzz")) // *buzz
            }
        }

        // And:
        // values present in ARM
        val positiveParameters = listOf(
            listOf("foo", "bar_", "_buzz"),
            listOf("foo", "bar_buzz"),
        )

        // Then:
        positiveParameters.forEach {
            assertTrue("Failed for scenario: $it") {
                composedCheck.matches(it)
            }
        }
    }
    @Test
    fun shouldFail() {
        // Given: check done on top of URM
        val composedCheck = Check().apply {
            useAllOf().apply {
                add(Equals("foo"))
                add(StartsWith("bar")) // bar*
                add(EndsWith("buzz")) // *buzz
            }
        }
        // And: values present in ARM
        val negativeParameters = listOf(
            listOf("foo"),
            listOf("bar_", "_buzz"),
            listOf("bar_buzz"),
            "foo",
            "bar_",
            "_buzz"
        )

        // Then:
        negativeParameters.forEach {
            assertFalse("Succeeded for negative scenario: $it") {
                composedCheck.matches(it)
            }
        }
    }
}