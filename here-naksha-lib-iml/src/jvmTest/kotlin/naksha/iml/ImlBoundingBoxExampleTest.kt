package naksha.iml

import java.util.UUID
import naksha.geo.SpBoundingBox
import naksha.model.Naksha
import naksha.model.NakshaContext
import naksha.model.SessionOptions
import naksha.model.objects.XyzMembers
import naksha.model.request.ReadFeatures
import naksha.model.request.SuccessResponse
import naksha.model.request.ops.Intersects
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

/**
 * Standalone read-only bounding-box example. Supply NAKSHA_TEST_IML_REMOTE_DB_URL to run against an existing IML database.
 * @since 3.0
 */
class ImlBoundingBoxExampleTest {
  @Test
  fun `read features intersecting a bounding box`() {
    val url = System.getenv("NAKSHA_TEST_IML_REMOTE_DB_URL")
    assumeTrue(!url.isNullOrBlank(), "Set NAKSHA_TEST_IML_REMOTE_DB_URL to run this example")
    val layer = System.getenv("NAKSHA_TEST_IML_REMOTE_LAYER")
      ?: "hrn:here-dev:data::olp-here:m499-drgnstn-source-08:amz-20m"

    NakshaContext.currentContext().withAppId("iml-bbox-example")
    val config = ImlConfig("iml-bbox-example-${UUID.randomUUID()}", url.orEmpty()).apply {
      hardCap = 100
    }
    try {
      val storage = Naksha.useStorage(config)
      storage.newReadSession(SessionOptions(
        connectTimeout = 15000,
        socketTimeout = 60000,
        stmtTimeout = 45000
      )).use { session ->
        // WGS84 longitude/latitude: west, south, east, north.
        val bbox = SpBoundingBox(78.53, 17.30, 78.55, 17.33)
        val response = session.executeRead(ReadFeatures().apply {
          catalogId = "public"
          collectionId = layer
          limit = 100
          queryMembers = Intersects(XyzMembers.XyzGeometry, bbox.toPolygon())
        }) as SuccessResponse

        // The query selects tuple identities; accessing features loads their exact states.
        val features = response.features
        println("Bounding-box query returned ${features.size} features (limit 100)")
        assertTrue(features.isNotEmpty(), "Expected features in the example bounding box")
        assertTrue(features.size <= 100)
        for (feature in features) {
          assertNotNull(feature)
          assertNotNull(feature?.geometry)
          println(feature?.id)
        }
      }
    } finally {
      Naksha.removeStorage(config)
    }
  }
}
