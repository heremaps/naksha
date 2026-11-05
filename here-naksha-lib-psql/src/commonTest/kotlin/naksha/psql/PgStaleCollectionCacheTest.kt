package naksha.psql

import naksha.base.Id
import naksha.base.NakshaError
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.StoreMode
import naksha.model.request.ErrorResponse
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class PgStaleCollectionCacheTest : PgTestBase(collection = null) {
  @Test
  fun staleCollectionIsEvictedAfterFailedInsert() {
    for (history in listOf(false, true)) {
      for (aliased in listOf(false, true)) staleCollectionIsEvictedAfterFailedInsert(history, aliased)
    }
  }

  private fun staleCollectionIsEvictedAfterFailedInsert(history: Boolean, aliased: Boolean) {
    val collectionId = if (aliased) "stale_collection_with_a_long_logical_identifier_$history" else "stale_collection_$history"
    val descriptor = NakshaCollection(collectionId, catalog.id).apply {
      storeHistory = if (history) StoreMode.ON else StoreMode.OFF
    }
    storage.newWriteSession(newSessionOptions()).use { session ->
      assertSuccess(session.executeWrite(WriteRequest().add(Write().createCollection(descriptor))))
      session.commit()
    }

    val catalogNumber = Id(catalog.id).intValue
    val collectionNumber = Id(collectionId).intValue
    val cached = storage.newReadSession(newSessionOptions()).use { session ->
      val loaded = assertNotNull(session.getCollectionById(catalog, collectionId))
      val entry = assertNotNull(storage.database.getPgCollection(catalogNumber, collectionNumber))
      assertSame(entry.naksha, loaded)
      entry
    }
    storage.newReadSession(newSessionOptions()).use { session ->
      assertSame(cached.naksha, session.getCollectionById(catalog, collectionId))
    }

    storage.newWriteSession(newSessionOptions()).use { session ->
      assertSuccess(session.executeWrite(WriteRequest().add(Write().deleteCollectionById(catalog.id, collectionId))))
      session.commit()
    }
    assertNull(storage.database.getPgCollection(catalogNumber, collectionNumber), "DELETE must evict the shared cache")

    storage.database.cachePgCollection(cached)
    storage.newWriteSession(newSessionOptions()).use { session ->
      (session as PgSession).cachePgCollection(cached)
      assertSame(cached.naksha, session.getCollectionById(catalog, collectionId), "The injected stale entry must actually be used")
      val response = session.executeWrite(WriteRequest().add(Write().createFeature(descriptor, NakshaFeature("not_inserted"))))
      val error = assertIs<ErrorResponse>(response).error
      assertEquals(NakshaError.COLLECTION_NOT_FOUND, error.code, error.msg)
      assertEquals(storage.id.text, error.database)
      assertEquals(catalog.id, error.catalog)
      assertEquals(collectionId, error.collection)
      assertNull(storage.database.getPgCollection(catalogNumber, collectionNumber), "The failure must evict the shared stale entry")
      assertNull(session.getPgCollection(catalogNumber, collectionNumber), "The failure must also evict the session entry")
      session.rollback()
    }
    storage.newReadSession(newSessionOptions()).use { session ->
      assertNull(session.getCollectionById(catalog, collectionId))
    }
  }
}
