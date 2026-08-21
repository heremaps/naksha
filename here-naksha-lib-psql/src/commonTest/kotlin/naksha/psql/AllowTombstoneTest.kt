package naksha.psql

import naksha.model.objects.NakshaCollection
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies that deleted catalogs and collections are not returned by default (i.e. when
 * [naksha.model.ISession.getCatalogById] / [naksha.model.ISession.getCollectionById] are called
 * without `allowTombstone = true`), and that they **are** returned when `allowTombstone = true`.
 */
class AllowTombstoneTest : PgTestBase() {

    @Test
    fun deletedCollectionShouldNotBeReturnedByDefault() {
        val collectionId = "tombstone_col_test"
        val collection = NakshaCollection(collectionId, catalog.id)

        // Create the collection.
        executeWrite(WriteRequest().add(Write().createCollection(collection)))

        // Verify it is visible before deletion.
        newWriteSession().use { session ->
            val found = session.getCatalogById(catalog.id)
            assertNotNull(found, "Catalog should exist before deleting the collection")
            assertNotNull(
                session.getCollectionById(found, collectionId),
                "Collection should be visible before deletion"
            )
        }

        // Delete the collection.
        executeWrite(WriteRequest().add(Write().deleteCollectionById(catalog.id, collectionId)))

        // Default call (allowTombstone = false) must return null.
        newWriteSession().use { session ->
            val cat = session.getCatalogById(catalog.id)
            assertNotNull(cat, "Catalog should still exist after collection deletion")
            assertNull(
                session.getCollectionById(cat, collectionId),
                "Deleted collection must NOT be returned when allowTombstone = false (default)"
            )

            // Explicit allowTombstone = true must return the tombstone entry.
            assertNotNull(
                session.getCollectionById(cat, collectionId, allowTombstone = true),
                "Deleted collection MUST be returned when allowTombstone = true"
            )
        }
    }

    @Test
    fun deletedCatalogShouldNotBeReturnedByDefault() {
        // Use a freshly created, dedicated catalog so that deleting it does not affect other tests.
        val dedicatedCatalogId = "tombstone_cat_test"

        // Create the dedicated catalog.
        executeWrite(WriteRequest().add(Write().createMap(naksha.model.objects.NakshaCatalog(dedicatedCatalogId))))

        // Verify it is visible before deletion.
        newReadSession().use { session ->
            assertNotNull(
                session.getCatalogById(dedicatedCatalogId),
                "Catalog should be visible before deletion"
            )
        }

        // Delete the catalog (use executeWrite directly since this catalog was not created via initCatalog).
        executeWrite(WriteRequest().add(Write().deleteMapById(dedicatedCatalogId)))

        // Default call (allowTombstone = false) must return null.
        newReadSession().use { session ->
            assertNull(
                session.getCatalogById(dedicatedCatalogId),
                "Deleted catalog must NOT be returned when allowTombstone = false (default)"
            )

            // Explicit allowTombstone = true must return the tombstone entry.
            assertNotNull(
                session.getCatalogById(dedicatedCatalogId, allowTombstone = true),
                "Deleted catalog MUST be returned when allowTombstone = true"
            )
        }
    }

    @Test
    fun recreatedCollectionShouldBeVisible() {
        val collectionId = "tombstone_recreate_col_test"
        val collection = NakshaCollection(collectionId, catalog.id)

        // Create the collection.
        executeWrite(WriteRequest().add(Write().createCollection(collection)))

        // Delete the collection.
        executeWrite(WriteRequest().add(Write().deleteCollectionById(catalog.id, collectionId)))

        // Verify the tombstone is present and the collection is not returned by default.
        newWriteSession().use { session ->
            val cat = session.getCatalogById(catalog.id)
            assertNotNull(cat, "Catalog should still exist after collection deletion")
            assertNull(
                session.getCollectionById(cat, collectionId),
                "Deleted collection must NOT be returned when allowTombstone = false (default)"
            )
        }

        // Re-create the collection with the same id.
        executeWrite(WriteRequest().add(Write().createCollection(NakshaCollection(collectionId, catalog.id))))

        // The re-created collection must be visible.
        newWriteSession().use { session ->
            val cat = session.getCatalogById(catalog.id)
            assertNotNull(cat, "Catalog should exist after collection re-creation")
            val recreated = session.getCollectionById(cat, collectionId)
            assertNotNull(recreated, "Re-created collection MUST be visible after re-creation")
            assertEquals(collectionId, recreated.id, "Re-created collection id must match")
        }
    }

    @Test
    fun deletingAlreadyDeletedCollectionShouldSucceed() {
        val collectionId = "tombstone_double_delete_col_test"
        val collection = NakshaCollection(collectionId, catalog.id)

        // Create the collection.
        executeWrite(WriteRequest().add(Write().createCollection(collection)))

        // Delete the collection (first deletion).
        executeWrite(WriteRequest().add(Write().deleteCollectionById(catalog.id, collectionId)))

        // Fetch the HEAD state including the tombstone and verify it is marked deleted.
        val tombstone = newWriteSession().use { session ->
            val cat = session.getCatalogById(catalog.id)
            assertNotNull(cat, "Catalog should still exist after first deletion")
            val ts = session.getCollectionById(cat, collectionId, allowTombstone = true)
            assertNotNull(ts, "Tombstone must be retrievable with allowTombstone = true")
            assertTrue(
                ts.tupleNumber?.isDeleted ?: false,
                "HEAD state must be marked as deleted (tombstone)"
            )
            ts
        }

        // Delete the already-deleted collection again, non-atomically (should succeed).
        executeWrite(WriteRequest().add(Write().deleteCollection(tombstone, atomic = false)))
    }
}
