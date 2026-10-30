package naksha.iml

import java.util.UUID
import naksha.base.Guid
import naksha.base.Id
import naksha.base.Version
import naksha.geo.SpBoundingBox
import naksha.model.Naksha
import naksha.model.NakshaContext
import naksha.model.SessionOptions
import naksha.model.objects.StandardMembers
import naksha.model.objects.XyzMembers
import naksha.model.request.ReadFeatures
import naksha.model.request.SuccessResponse
import naksha.model.request.ops.And
import naksha.model.request.ops.Equals
import naksha.model.request.ops.Intersects
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

/**
 * Opt-in, strictly read-only smoke test of an existing IML layer. Never uses the local schema-creating fixture.
 * @since 3.0
 */
class ImlRemoteReadTest {
  @Test
  fun `existing remote layer supports two phase first hundred ID bbox and author reads`() {
    val url = System.getenv("NAKSHA_TEST_IML_REMOTE_DB_URL")
    val layer = System.getenv("NAKSHA_TEST_IML_REMOTE_LAYER")
    assumeTrue(!url.isNullOrBlank() && !layer.isNullOrBlank(), "Set remote IML URL and logical layer ID to enable read-only smoke test")
    NakshaContext.currentContext().withAppId("iml-remote-test")
    val config = ImlConfig("iml-remote-${UUID.randomUUID()}", url.orEmpty()).apply { hardCap = 100 }
    try {
      val storage = Naksha.useStorage(config) as ImlStorage
      storage.newReadSession(SessionOptions(connectTimeout = 15000, socketTimeout = 30000, stmtTimeout = 20000)).use { session ->
        val catalog = checkNotNull(session.getCatalogById("public"))
        val collection = checkNotNull(session.getCollectionById(catalog, layer!!))
        assertEquals(Id.textToInt(layer), collection.collectionNumber)
        fun read(configure: ReadFeatures.() -> Unit = {}): SuccessResponse = session.executeRead(ReadFeatures().apply {
          catalogId = "public"
          collectionId = layer
          limit = 100
          configure()
        }) as SuccessResponse
        val selected = read().featureTupleList
        assertEquals(100, selected.size)
        for (entry in selected) assertNull(entry?.tuple)
        session.loadTuples(selected)
        for (entry in selected) {
          val tuple = checkNotNull(entry?.tuple)
          val feature = checkNotNull(entry.feature)
          assertEquals(tuple.id, feature.id)
          assertEquals(tuple.tupleNumber, Guid.fromString(feature.properties.xyz.uuid!!).tupleNumber)
          assertEquals(Version.HEAD.number, tuple.nextVersion)
          assertNotNull(feature.geometry)
          assertNotNull(feature.properties.xyz.getRaw("createdAt"))
          assertNotNull(feature.properties.xyz.getRaw("updatedAt"))
        }
        val first = checkNotNull(selected[0])
        val feature = checkNotNull(first.feature)
        val byId = read { queryMembers = Equals(StandardMembers.Id, feature.id) }.features
        assertEquals(1, byId.size)
        assertEquals(feature.id, byId[0]?.id)
        val bbox = SpBoundingBox(checkNotNull(feature.geometry).getCoordinates()).toPolygon()
        val filtered = read {
          queryMembers = And(Equals(StandardMembers.Id, feature.id), Intersects(XyzMembers.XyzGeometry, bbox), Equals(XyzMembers.XyzAuthor, first.tuple?.getString(XyzMembers.XyzAuthor)))
        }.features
        assertEquals(1, filtered.size)
        assertEquals(feature.id, filtered[0]?.id)
      }
    } finally {
      Naksha.removeStorage(config)
    }
  }
}
