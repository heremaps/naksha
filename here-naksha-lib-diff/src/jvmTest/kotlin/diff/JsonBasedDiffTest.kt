package diff

import naksha.base.Platform
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

// TODO: finish - this should be copy of old `PatcherTest`
internal class JsonBasedDiffTest {
    @Test
    fun shouldX() {
        // Given:
        val feature1 = getResourceAsText("feature_1.json")
        val f = Platform.fromJSON(feature1)

        // Then
        assertNotNull(feature1)
        assertNotNull(f)
    }

    private fun getResourceAsText(fileName: String): String =
        javaClass.getResource(JSON_DIFF_RESOURCES + fileName)?.readText()
            ?: throw IllegalArgumentException("Could not find/read text file: $JSON_DIFF_RESOURCES$fileName")

    companion object {
        private const val JSON_DIFF_RESOURCES = "/json_diff/"
    }
}
