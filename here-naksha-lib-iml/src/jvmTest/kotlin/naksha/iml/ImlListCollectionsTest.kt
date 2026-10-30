package naksha.iml

import java.util.UUID
import naksha.model.Naksha
import naksha.model.NakshaContext
import naksha.model.request.ReadCollections
import naksha.model.request.SuccessResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

/**
 * Lists every collection exposed by the configured IML catalog without reading or changing layer data.
 * @since 3.0
 */
class ImlListCollectionsTest {
  @Test
  fun `list all existing collections`() {
    val url = System.getenv("NAKSHA_TEST_IML_REMOTE_DB_URL")
    assumeTrue(!url.isNullOrBlank(), "Set NAKSHA_TEST_IML_REMOTE_DB_URL to list collections")
    NakshaContext.currentContext().withAppId("iml-list-collections")
    val config = ImlConfig("iml-list-${UUID.randomUUID()}", url.orEmpty())
    try {
      val storage = Naksha.useStorage(config) as ImlStorage
      storage.newReadSession().use { session ->
        val response = session.executeRead(ReadCollections().apply { mapId = config.schema }) as SuccessResponse
        val features = response.features
        val ids = HashSet<String>()
        for (feature in features) {
          val id = checkNotNull(feature).id
          assertTrue(ids.add(id), "Duplicate collection: $id")
          println(id)
        }
        assertTrue(features.isNotEmpty(), "Expected at least the virtual collections collection")
        assertEquals(storage.metadata.layersById.size + 1, features.size)
        println("Total: ${features.size} collections (${storage.metadata.layersById.size} physical layers)")
      }
    } finally {
      Naksha.removeStorage(config)
    }
  }
}
