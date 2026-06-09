package naksha.psql

import naksha.base.Int64
import naksha.model.*
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.StoreMode
import naksha.model.request.ReadFeatures
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

abstract class DeleteFeatureBase(
    collection: NakshaCollection? = NakshaCollection(""),
    mapId: String? = null
) : PgTestBase(collection, mapId) {

    @Test
    fun shouldPerformDelete() {
        val featureId = "feature_to_delete"
        val initialFeature = executeWrite(
            WriteRequest().add(
                Write().createFeature(collection.mapId, collection.id, NakshaFeature(featureId))
            )
        ).let { // this = SuccessResponse
            val features = assertNotNull(it.features)
            assertEquals(1, features.size)
            assertNotNull(features.first())
        }
        assertEquals(featureId, initialFeature.id)
        assertEquals(1 ,initialFeature.properties.xyz.changeCount)

        val deletedFeatures = executeWrite(
            WriteRequest().add(
                Write().deleteFeatureById(collection.mapId, collection.id, featureId)
            )
        ).let { // this = SuccessResponse
            val features = assertNotNull(it.features)
            assertEquals(1, features.size)
            assertNotNull(features.first())
        }
        assertEquals(featureId, deletedFeatures.id)
        assertEquals(2 ,deletedFeatures.properties.xyz.changeCount)

        // Verify that the feature does not exist
        Naksha.cache.clear()
        executeRead(ReadFeatures().apply {
            mapId = collection.mapId
            collectionIds += collection.id
            featureIds += initialFeature.id
        }).let { // this = SuccessResponse
            val features = assertNotNull(it.features)
            assertEquals(0, features.size)
        }

        // queryHistory=true (without queryDeleted) returns only past states from the history table.
        // The tombstone is in HEAD and is NOT included unless queryDeleted=true is also set.
        executeRead(ReadFeatures().apply {
            mapId = collection.mapId
            collectionIds += collection.id
            featureIds += initialFeature.id
            queryHistory = true
            versions = 10
        }).apply { // this = SuccessResponse
            assertEquals(1, features.size)
            val firstTuple = featureTupleList[0]?.tuple
            assertSame(Action.CREATED, Action.fromValue((firstTuple?.getLongMember(naksha.model.objects.StandardMembers.Version)?.toInt() ?: -1) and 3))
        }

        // verify if delete table contains element
        executeRead(ReadFeatures().apply {
            mapId = collection.mapId
            collectionIds += collection.id
            featureIds += initialFeature.id
            queryDeleted = true
        }).apply { // this = SuccessResponse
            assertEquals(1, features.size)
            val deletedFeature = assertNotNull(features[0])
            assertEquals(initialFeature.id, deletedFeature.id)
            assertEquals(Action.DELETED, deletedFeature.properties.xyz.action)
        }
    }

    @Test
    fun tombstoneVersionMustCarryDeleteTransactionAndDeletedActionBits() {
        val featureId = "feature_version_bits_check"
        val ACTION_MASK = Int64(3L)         // lower 2 bits
        val ACTION_CLEAR = Int64(-4L)       // clear lower 2 bits

        // CREATE — capture the create-transaction's version (txn with lower 2 bits = 0 = CREATED).
        val createdTn = assertNotNull(
            executeWrite(WriteRequest().add(Write().createFeature(collection, NakshaFeature(featureId))))
                .features.first()!!.properties.xyz.guid?.tupleNumber
        )
        val createTxn = createdTn.version.txn
        assertEquals(0L, (createTxn and ACTION_MASK).toLong(),
            "CREATE version must have action bits = 0 (CREATED)")

        // UPDATE — different transaction, different txn.
        val updatedTn = assertNotNull(
            executeWrite(WriteRequest().add(Write().updateFeature(collection, NakshaFeature(featureId), false)))
                .features.first()!!.properties.xyz.guid?.tupleNumber
        )
        val updateTxn = updatedTn.version.txn
        assertEquals(1L, (updateTxn and ACTION_MASK).toLong(),
            "UPDATE version must have action bits = 1 (UPDATED)")
        assertTrue((updateTxn and ACTION_CLEAR) > (createTxn and ACTION_CLEAR),
            "UPDATE transaction must be newer than CREATE transaction")

        // DELETE — the tombstone version must be: current delete-txn (new, > update-txn) | 2.
        val deletedTn = assertNotNull(
            executeWrite(WriteRequest().add(Write().deleteFeatureById(collection, featureId)))
                .features.first()!!.properties.xyz.guid?.tupleNumber
        )
        val deleteTxn = deletedTn.version.txn

        // Lower 2 bits must be 2 (DELETED action).
        assertEquals(2L, (deleteTxn and ACTION_MASK).toLong(),
            "Tombstone version must have action bits = 2 (DELETED)")

        // The transaction part (upper bits, lower 2 cleared) must be strictly newer than the UPDATE txn.
        assertTrue((deleteTxn and ACTION_CLEAR) > (updateTxn and ACTION_CLEAR),
            "DELETE transaction must be newer than UPDATE transaction")

        // Crucially: the transaction part must NOT be the old CREATE or UPDATE txn with its bits mangled —
        // it must be a genuinely new transaction number from the delete operation.
        assertTrue((deleteTxn and ACTION_CLEAR) != (createTxn and ACTION_CLEAR),
            "Tombstone transaction part must differ from CREATE transaction")
        assertTrue((deleteTxn and ACTION_CLEAR) != (updateTxn and ACTION_CLEAR),
            "Tombstone transaction part must differ from UPDATE transaction")

        // Confirm the tombstone is visible via queryDeleted and has the right action.
        Naksha.cache.clear()
        executeRead(ReadFeatures().apply {
            mapId = collection.mapId
            collectionIds += collection.id
            featureIds += featureId
            queryDeleted = true
        }).apply {
            assertEquals(1, features.size)
            val tombstone = assertNotNull(features[0])
            assertEquals(Action.DELETED, tombstone.properties.xyz.action)
            // The raw version from HEAD must match exactly what the DELETE write returned.
            assertEquals(deleteTxn, tombstone.properties.xyz.guid?.tupleNumber?.version?.txn)
        }
    }

    @Test
    fun deleteWithoutHistoryButWithShadow() {
        // Create special test collection.
        val createCollectionReq = WriteRequest().add(
            Write().createCollection(
                NakshaCollection("delete_no_history_but_shadow", map.id)
                    .withStoreDeleted(StoreMode.ON)
                    .withStoreMeta(StoreMode.OFF)
                    .withStoreHistory(StoreMode.OFF)
            )
        )
        val createCollectionResp = executeWrite(createCollectionReq)
        assertEquals(1, createCollectionResp.length)
        assertEquals(1, createCollectionResp.features.size)
        val collection = assertNotNull(createCollectionResp.features[0]).proxy(NakshaCollection::class)
        assertEquals(map.id, collection.mapId)
        assertEquals("delete_no_history_but_shadow", collection.id)

        // Create feature.
        val featureId = "feature_to_delete_without_history"
        val createFeatureReq = WriteRequest().add(
            Write().createFeature(collection, NakshaFeature(featureId))
        )
        val createFeatureResp = executeWrite(createFeatureReq)
        assertEquals(1, createFeatureResp.length)
        assertEquals(1, createFeatureResp.features.size)
        val feature = assertNotNull(createFeatureResp.features[0])

        // Delete the feature.
        val deleteFeaturesReq = WriteRequest().add(
            Write().deleteFeatureById(collection, featureId)
        )
        val deleteFeatureResp = executeWrite(deleteFeaturesReq)
        assertEquals(1, deleteFeatureResp.length)
        assertEquals(1, deleteFeatureResp.features.size)
        val deleteFeature = assertNotNull(deleteFeatureResp.features[0])
        assertEquals(feature.id, deleteFeature.id)
    }
}
