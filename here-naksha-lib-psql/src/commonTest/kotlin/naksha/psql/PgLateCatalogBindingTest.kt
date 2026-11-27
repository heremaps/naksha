package naksha.psql

import naksha.base.Id
import naksha.base.NakshaError
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.request.ErrorResponse
import naksha.model.request.ReadFeatures
import naksha.model.request.SuccessResponse
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class PgLateCatalogBindingTest : PgTestBase(collection = null) {
  @Test
  fun unresolvedCatalogIsRejectedAtExecutionAndResolvedDeleteSucceeds() {
    useCollection("late_catalog_binding")
    executeWrite(WriteRequest().add(Write().createFeature(collection, NakshaFeature("kept"))))
    val write = Write().deleteFeatureById(NakshaCollection(collection.id), "kept")
    val read = ReadFeatures().apply {
      catalogId = catalog.id
      collectionId = collection.id
      featureIds.add("kept")
    }
    assertNull(write.catalogId)
    storage.newWriteSession(newSessionOptions()).use { session ->
      val rejected = assertIs<ErrorResponse>(session.executeWrite(WriteRequest().add(write)))
      assertEquals(NakshaError.ILLEGAL_ARGUMENT, rejected.error.code)
      session.rollback()
    }
    storage.newReadSession(newSessionOptions()).use { session ->
      assertEquals(1, assertIs<SuccessResponse>(session.executeRead(read)).features.size)
    }
    write.catalogId = Id(catalog.id)
    executeWrite(WriteRequest().add(write))
    storage.newReadSession(newSessionOptions()).use { session ->
      assertEquals(0, assertIs<SuccessResponse>(session.executeRead(read)).features.size)
    }
  }
}
