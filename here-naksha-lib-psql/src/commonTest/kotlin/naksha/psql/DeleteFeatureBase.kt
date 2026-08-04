package naksha.psql

import naksha.base.Action
import naksha.base.Id
import naksha.base.Int64
import naksha.model.Naksha
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
    collection: NakshaCollection? = NakshaCollection(),
    mapId: String? = null
) : PgTestBase(collection, mapId) {

    @Test
    fun shouldPerformDelete() {
        val featureId = "feature_to_delete"
        val initialFeature = executeWriteAndLoadTuples(
            WriteRequest().add(
                Write().createFeature(collection, NakshaFeature(Id(featureId)))
            )
        ).let { // this = SuccessResponse
            val features = it.asFeatures
            assertEquals(1, features.size)
            assertNotNull(features.first())
        }
        assertEquals(featureId, initialFeature.id.text)
        assertEquals(1 ,initialFeature.properties.xyz.changeCount)

        val deletedFeatures = executeWriteAndLoadTuples(
            WriteRequest().add(
                Write().deleteFeatureById(collection, Id(featureId))
            )
        ).let { // this = SuccessResponse
            val features = it.asFeatures
            assertEquals(1, features.size)
            assertNotNull(features.first())
        }
        assertEquals(featureId, deletedFeatures.id.text)
        assertEquals(2 ,deletedFeatures.properties.xyz.changeCount)

        // Verify that the feature does not exist
        Naksha.cache.clear()
        executeReadAndLoadTuple(ReadFeatures().apply {
            catalogId = collection.catalogId
            collectionId = collection.id
            featureIds += initialFeature.id
        }).let { // this = SuccessResponse
            val features = it.asFeatures
            assertEquals(0, features.size)
        }

        // queryHistory=true (without queryDeleted) returns only past states from the history table.
        // The tombstone is in HEAD and is NOT included unless queryDeleted=true is also set.
        executeReadAndLoadTuple(ReadFeatures().apply {
            catalogId = collection.catalogId
            collectionId = collection.id
            featureIds += initialFeature.id
            queryHistory = true
            versions = 10
        }).apply { // this = SuccessResponse
            assertEquals(1, asFeatures.size)
            val rs = resultSet
            val firstTupleNumber = rs?.getTupleNumber(0)
            val tuples = arrayOfNulls<naksha.model.Tuple>(1)
            if (firstTupleNumber != null) {
                Naksha.cache.load(tuples, arrayOf(firstTupleNumber))
            }
            val firstTuple = tuples[0]
            assertSame(Action.CREATE, Action.fromValue((firstTuple?.getLong(naksha.model.objects.StandardMembers.VersionMember)?.toInt() ?: -1) and 3))
        }

        // verify if delete table contains element
        executeReadAndLoadTuple(ReadFeatures().apply {
            catalogId = collection.catalogId
            collectionId = collection.id
            featureIds += initialFeature.id
            queryDeleted = true
        }).apply { // this = SuccessResponse
            assertEquals(1, asFeatures.size)
            val deletedFeature = assertNotNull(asFeatures[0])
            assertEquals(initialFeature.id, deletedFeature.id)
            assertEquals(Action.DELETE, deletedFeature.properties.xyz.action)
        }
    }

    @Test
    fun tombstoneVersionMustCarryDeleteTransactionAndDeletedActionBits() {
        val featureId = "feature_version_bits_check"
        val ACTION_MASK = 3L         // lower 2 bits
        val ACTION_CLEAR = -4L       // clear lower 2 bits

        // CREATE — capture the create-transaction's version (txn with lower 2 bits = 0 = CREATED).
        val createdTn = assertNotNull(
            executeWriteAndLoadTuples(WriteRequest().add(Write().createFeature(collection, NakshaFeature(Id(featureId)))))
                .asFeatures.first()!!.properties.xyz.guid?.tupleNumber
        )
        val createTxn = createdTn.version
        assertEquals(0L, (createTxn and ACTION_MASK).toLong(),
            "CREATE version must have action bits = 0 (CREATED)")

        // UPDATE — different transaction, different txn.
        val updatedTn = assertNotNull(
            executeWriteAndLoadTuples(WriteRequest().add(Write().updateFeature(collection, NakshaFeature(Id(featureId)), false)))
                .asFeatures.first()!!.properties.xyz.guid?.tupleNumber
        )
        val updateTxn = updatedTn.version
        assertEquals(1L, updateTxn and ACTION_MASK,
            "UPDATE version must have action bits = 1 (UPDATED)")
        assertTrue((updateTxn and ACTION_CLEAR) > (createTxn and ACTION_CLEAR),
            "UPDATE transaction must be newer than CREATE transaction")

        // DELETE — the tombstone version must be: current delete-txn (new, > update-txn) | 2.
        val deletedTn = assertNotNull(
            executeWriteAndLoadTuples(WriteRequest().add(Write().deleteFeatureById(collection, Id(featureId))))
                .asFeatures.first()!!.properties.xyz.guid?.tupleNumber
        )
        val deleteTxn = deletedTn.version

        // Lower 2 bits must be 2 (DELETED action).
        assertEquals(2L, deleteTxn and ACTION_MASK,
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
        executeReadAndLoadTuple(ReadFeatures().apply {
            catalogId = collection.catalogId
            collectionId = collection.id
            featureIds += Id(featureId)
            queryDeleted = true
        }).apply {
            assertEquals(1, asFeatures.size)
            val tombstone = assertNotNull(asFeatures[0])
            assertEquals(Action.DELETE, tombstone.properties.xyz.action)
            // The raw version from HEAD must match exactly what the DELETE write returned.
            assertEquals(deleteTxn, tombstone.properties.xyz.guid?.tupleNumber?.version)
        }
    }

    @Test
    fun deleteWithoutHistoryButWithShadow() {
        // Create special test collection.
        val createCollectionReq = WriteRequest().add(
            Write().createCollection(
                NakshaCollection(Id("delete_no_history_but_shadow"), catalog)
                    .withStoreDeleted(StoreMode.ON)
                    .withStoreMeta(StoreMode.OFF)
                    .withStoreHistory(StoreMode.OFF)
            )
        )
        val createCollectionResp = executeWriteAndLoadTuples(createCollectionReq)
        assertEquals(1, createCollectionResp.length)
        assertEquals(1, createCollectionResp.asFeatures.size)
        val collection = assertNotNull(createCollectionResp.asFeatures[0]).proxy(NakshaCollection::class)
        assertEquals(catalog.id, collection.catalogId)
        assertEquals("delete_no_history_but_shadow", collection.id.text)

        // Create feature.
        val featureId = "feature_to_delete_without_history"
        val createFeatureReq = WriteRequest().add(
            Write().createFeature(collection, NakshaFeature(Id(featureId)))
        )
        val createFeatureResp = executeWriteAndLoadTuples(createFeatureReq)
        assertEquals(1, createFeatureResp.length)
        assertEquals(1, createFeatureResp.asFeatures.size)
        val feature = assertNotNull(createFeatureResp.asFeatures[0])

        // Delete the feature.
        val deleteFeaturesReq = WriteRequest().add(
            Write().deleteFeatureById(collection, Id(featureId))
        )
        val deleteFeatureResp = executeWriteAndLoadTuples(deleteFeaturesReq)
        assertEquals(1, deleteFeatureResp.length)
        assertEquals(1, deleteFeatureResp.asFeatures.size)
        val deleteFeature = assertNotNull(deleteFeatureResp.asFeatures[0])
        assertEquals(feature.id, deleteFeature.id)
    }
}
