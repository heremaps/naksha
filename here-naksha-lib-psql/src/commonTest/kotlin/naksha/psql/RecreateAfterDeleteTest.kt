package naksha.psql

import naksha.model.Action
import naksha.model.Naksha
import naksha.model.objects.NakshaFeature
import naksha.model.request.ReadFeatures
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies the auto-purge behaviour when a feature is re-created after deletion.
 *
 * When a CREATE is issued for a feature whose HEAD row is a tombstone (deleted state), the storage
 * must automatically archive the tombstone into history, then place the new feature into HEAD —
 * exactly as if the tombstone had been explicitly purged before re-inserting.
 *
 * Full lifecycle under test:
 *   CREATE → UPDATE → DELETE → CREATE (auto-purge of tombstone) → verify history
 */
class RecreateAfterDeleteTest : PgTestBase() {

    @Test
    fun shouldAutoPurgeTombstoneOnRecreate() {
        val featureId = "feature_recreate_test"

        // Step 1: CREATE
        val created = executeWrite(
            WriteRequest().add(Write().createFeature(collection, NakshaFeature(featureId)))
        ).features.first()!!
        assertEquals(featureId, created.id)
        assertEquals(Action.CREATE, created.properties.xyz.action)
        assertEquals(1, created.properties.xyz.changeCount)

        // Step 2: UPDATE — build on the returned created feature so cc is correct
        val updatedFeature = NakshaFeature(featureId).also { it.properties["marker"] = "updated" }
        executeWrite(
            WriteRequest().add(Write().updateFeature(collection, updatedFeature, false))
        )
        Naksha.cache.clear()
        val updated = executeRead(ReadFeatures().apply {
            catalogId = collection.catalogId
            collectionId += collection.id
            featureIds += featureId
        }).features.first()!!
        assertEquals(featureId, updated.id)
        assertEquals(Action.UPDATE, updated.properties.xyz.action)

        // Step 3: DELETE — tombstone now lives in HEAD
        executeWrite(
            WriteRequest().add(Write().deleteFeatureById(collection, featureId))
        )
        // Confirm tombstone is visible via queryDeleted=true
        Naksha.cache.clear()
        val deleted = executeRead(ReadFeatures().apply {
            catalogId = collection.catalogId
            collectionId += collection.id
            featureIds += featureId
            queryDeleted = true
        }).features.first()!!
        assertEquals(featureId, deleted.id)
        assertEquals(Action.DELETE, deleted.properties.xyz.action)

        // Confirm feature is invisible in a normal read
        Naksha.cache.clear()
        val notFound = executeRead(ReadFeatures().apply {
            catalogId = collection.catalogId
            collectionId += collection.id
            featureIds += featureId
        })
        assertEquals(0, notFound.features.size)

        // Step 4: RE-CREATE — must auto-purge the tombstone and insert fresh
        val recreated = executeWrite(
            WriteRequest().add(Write().createFeature(collection, NakshaFeature(featureId).apply {
                properties["marker"] = "recreated"
            }))
        ).features.first()!!
        assertEquals(featureId, recreated.id)
        assertEquals(Action.CREATE, recreated.properties.xyz.action)
        // cc resets to 1 for the new lifecycle
        assertEquals(1, recreated.properties.xyz.changeCount)

        // Confirm feature is visible again in a normal read
        Naksha.cache.clear()
        val found = executeRead(ReadFeatures().apply {
            catalogId = collection.catalogId
            collectionId += collection.id
            featureIds += featureId
        })
        assertEquals(1, found.features.size)
        assertEquals(Action.CREATE, found.features[0]!!.properties.xyz.action)

        // queryHistory=true returns current HEAD (live CREATED) + all history entries.
        // History after auto-purge: DELETED (archived tombstone), UPDATED, CREATED (old lifecycle).
        // Total = 1 (HEAD) + 3 (history) = 4, in descending version order.
        val historyOnly = executeRead(ReadFeatures().apply {
            catalogId = collection.catalogId
            collectionId += collection.id
            featureIds += featureId
            queryHistory = true
            versions = 10
        })
        assertEquals(4, historyOnly.features.size)
        assertEquals(Action.CREATE, historyOnly.features[0]!!.properties.xyz.action)  // new HEAD
        assertEquals(Action.DELETE, historyOnly.features[1]!!.properties.xyz.action)  // archived tombstone
        assertEquals(Action.UPDATE, historyOnly.features[2]!!.properties.xyz.action)
        assertEquals(Action.CREATE, historyOnly.features[3]!!.properties.xyz.action)

        // queryHistory + queryDeleted: same result — no tombstone in HEAD (was auto-purged),
        // so queryDeleted=true adds nothing here.
        val full = executeRead(ReadFeatures().apply {
            catalogId = collection.catalogId
            collectionId += collection.id
            featureIds += featureId
            queryHistory = true
            queryDeleted = true
            versions = 10
        })
        assertEquals(4, full.features.size)
        assertEquals(Action.CREATE, full.features[0]!!.properties.xyz.action)
        assertEquals(Action.DELETE, full.features[1]!!.properties.xyz.action)
        assertEquals(Action.UPDATE, full.features[2]!!.properties.xyz.action)
        assertEquals(Action.CREATE, full.features[3]!!.properties.xyz.action)
    }
}
