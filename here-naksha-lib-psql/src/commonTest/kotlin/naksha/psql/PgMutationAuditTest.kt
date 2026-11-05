package naksha.psql

import naksha.base.Action
import naksha.base.Id
import naksha.base.NakshaError
import naksha.base.NakshaException
import naksha.base.TupleNumber
import naksha.base.Version
import naksha.base.proxy
import naksha.jbon.HeapBook
import naksha.model.NewTuple
import naksha.base.Guid
import naksha.model.Tuple
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaCatalog
import naksha.model.objects.NakshaFeature
import naksha.model.objects.StandardMembers
import naksha.model.objects.StoreMode
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PgMutationAuditTest : PgTestBase(collection = null) {
  @Test
  fun changeCountBoundaryAndOverflowLeaveHeadUnchanged() {
    for (history in listOf(false, true)) {
      useCollection(NakshaCollection("audit_cc_$history", catalog.id).apply { storeHistory = if (history) StoreMode.ON else StoreMode.OFF })
      val created = executeWrite(WriteRequest().add(Write().createFeature(collection, NakshaFeature("audit_cc"))))
      val original = assertNotNull(assertNotNull(created.featureTupleList[0]).tuple)
      storage.adminConnection().use { conn ->
        val pgCollection = assertNotNull(storage.database.getPgCollection(Id(catalog.id).intValue, Id(collection.id).intValue, conn))
        val cc = assertNotNull(pgCollection.columns.firstOrNull { it.name == StandardMembers.ChangeCount.name })
        val target = "${PgUtil.quoteIdent(pgCollection.catalogId.catalogName())}.${PgUtil.quoteIdent(pgCollection.id.collectionName())}"
        conn.execute("SAVEPOINT audit_cc").close()
        conn.execute("UPDATE $target SET ${cc.ident} = ${Int.MAX_VALUE - 1} WHERE nv = ${Version.HEAD.number}").close()
        conn.execute("SAVEPOINT audit_delete_boundary").close()
        val deleted = storage.database.rows_delete_by_id_with_result(conn, pgCollection, original.version + 4L, listOf(original.id))[0]
        assertEquals(Int.MAX_VALUE, deleted.tuple.membersBook[StandardMembers.ChangeCount.name])
        assertEquals(Action.DELETE, deleted.tuple.action)
        conn.execute("ROLLBACK TO SAVEPOINT audit_delete_boundary").close()
        val book = HeapBook.copyOf(original.membersBook)
        book.put(StandardMembers.TN, TupleNumber.copy(original.tupleNumber, version = (original.version and -4L) + 4L))
        val incoming = NewTuple(Tuple(original.featureBytes, book), null)
        val updated = storage.database.rows_upsert(conn, pgCollection.catalogId, pgCollection.id, listOf(incoming), history, pgCollection.columns)[0]
        assertEquals(Int.MAX_VALUE, updated.tuple.membersBook[StandardMembers.ChangeCount.name])
        assertEquals(Action.UPDATE, updated.tuple.action)

        val overflowBook = HeapBook.copyOf(incoming.tuple.membersBook)
        overflowBook.put(StandardMembers.TN, TupleNumber.copy(incoming.tuple.tupleNumber,
          version = (updated.tuple.version and -4L) + 4L))
        val overflow = Tuple(incoming.tuple.featureBytes, overflowBook)
        val upsertError = assertFailsWith<NakshaException> {
          storage.database.rows_upsert(conn, pgCollection.catalogId, pgCollection.id, listOf(NewTuple(overflow)), history, pgCollection.columns)
        }.error
        assertOverflowContext(upsertError, original.id.text, updated.tuple.version)
        val atomicUpsertError = assertFailsWith<NakshaException> {
          storage.database.rows_upsert(conn, pgCollection.catalogId, pgCollection.id,
            listOf(NewTuple(overflow, updated.tuple.tupleNumber)), history, pgCollection.columns)
        }.error
        assertEquals(updated.tuple.version, atomicUpsertError.expectedVersion)
        assertEquals(updated.tuple.version, atomicUpsertError.actualVersion)
        assertEquals(original.id.text, atomicUpsertError.id)
        val deleteError = assertFailsWith<NakshaException> {
          storage.database.rows_delete_by_id_with_result(conn, pgCollection, original.version + 8L, listOf(original.id))
        }.error
        assertOverflowContext(deleteError, original.id.text, updated.tuple.version)
        val expected = Guid(original.id.text, updated.tuple.tupleNumber)
        val atomicDeleteError = assertFailsWith<NakshaException> {
          storage.database.rows_delete_by_guid_with_result(conn, pgCollection, original.version + 8L, listOf(expected))
        }.error
        assertEquals(updated.tuple.version, atomicDeleteError.expectedVersion)
        assertEquals(updated.tuple.version, atomicDeleteError.actualVersion)
        assertEquals(original.id.text, atomicDeleteError.id)
        conn.execute("SELECT version::text AS version, ${cc.ident}::text AS cc FROM $target WHERE nv = ${Version.HEAD.number}").use { cursor ->
          assertTrue(cursor.next())
          assertEquals(updated.tuple.version.toString(), cursor.get<String>("version"))
          assertEquals(Int.MAX_VALUE.toString(), cursor.get<String>("cc"))
        }
        conn.execute("ROLLBACK TO SAVEPOINT audit_cc").close()
        conn.rollback()
      }
    }
  }

  private fun assertOverflowContext(error: NakshaError, featureId: String, actual: Long) {
    assertEquals(NakshaError.ILLEGAL_STATE, error.code)
    assertTrue(error.msg.contains("Change count"))
    assertEquals(storage.id.text, error.database)
    assertEquals(catalog.id, error.catalog)
    assertEquals(collection.id, error.collection)
    assertEquals(featureId, error.id)
    assertEquals(actual, error.actualVersion)
    assertNull(error.expectedVersion)
  }

  @Test
  fun metadataBatchMissNamesFailingCollectionAndKeepsContext() {
    val ids = listOf("audit_metadata_a", "audit_metadata_b").sortedWith { a, b -> Id(a).compareTo(Id(b)) }
    val existing = useCollection(NakshaCollection(ids[0], catalog.id)).second
    val missing = NakshaCollection(ids[1], catalog.id)
    val error = executeWriteErrorResponse(WriteRequest()
      .add(Write().updateCollection(existing, false))
      .add(Write().updateCollection(missing, false))).error
    assertEquals(NakshaError.COLLECTION_NOT_FOUND, error.code)
    assertEquals(missing.id, error.id)
    assertTrue(error.msg.contains(missing.id))
    assertEquals(storage.id.text, error.database)
    assertEquals(catalog.id, error.catalog)
    assertEquals(Id.COLLECTIONS_COL_ID.text, error.collection)
    assertNull(error.actualVersion)
    assertNull(error.expectedVersion)
  }

  @Test
  fun metadataBatchMissNamesFailingCatalogAndKeepsContext() {
    val ids = listOf("audit_catalog_a", "audit_catalog_b").sortedWith { a, b -> Id(a).compareTo(Id(b)) }
    val existing = assertNotNull(executeWrite(WriteRequest().add(Write().createCatalog(NakshaCatalog(storage.id, ids[0])))).features[0])
    val missing = NakshaCatalog(storage.id, ids[1])
    val error = executeWriteErrorResponse(WriteRequest()
      .add(Write().updateCatalog(existing.proxy(NakshaCatalog::class), false))
      .add(Write().updateCatalog(missing, false))).error
    assertEquals(NakshaError.CATALOG_NOT_FOUND, error.code)
    assertEquals(missing.id, error.id)
    assertTrue(error.msg.contains(missing.id))
    assertEquals(storage.id.text, error.database)
    assertEquals(Id.ADMIN_CATALOG_ID.text, error.catalog)
    assertEquals(Id.CATALOGS_COL_ID.text, error.collection)
    assertNull(error.actualVersion)
    assertNull(error.expectedVersion)
  }

  @Test
  fun atomicWithoutVersionIsRejectedByExecutorNotBuilder() {
    testWithCollection("audit_missing_version")
    val feature = NakshaFeature("audit_missing_version")
    for (write in listOf(Write().updateFeature(collection, feature, true), Write().upsertFeature(collection, feature, true))) {
      assertTrue(write.atomic)
      assertNull(write.version)
      val error = executeWriteErrorResponse(WriteRequest().add(write)).error
      assertEquals(NakshaError.ILLEGAL_ARGUMENT, error.code)
    }
  }
}
