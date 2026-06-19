package naksha.psql

import naksha.model.Action
import naksha.model.Naksha
import naksha.model.RandomFeatures
import naksha.model.objects.NakshaCollection
import naksha.model.objects.StoreMode
import naksha.model.request.ReadFeatures
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class HistoryUuidTest: PgTestBase(NakshaCollection(
    id = "history_puuid_test_collection",
    storeHistory = StoreMode.ON,
    storeDeleted = StoreMode.ON
)) {

    @Test
    fun shouldFormCorrectUuidSequenceOnUpdate(){
        // Given:
        val feature = RandomFeatures.randomFeature().apply { title = "initial_version" }

        // When:
        val createFeatureReq = WriteRequest().add(Write().createFeature(collection, feature))
        val createdFeature = executeWrite(createFeatureReq).features.first()!!

        // And:
        val updateFeatureReq = WriteRequest().add(Write().updateFeature(collection, createdFeature.apply {
            title = "updated_version"
        }, atomic = true))
        executeWrite(updateFeatureReq)

        // And:
        val deleteFeatureReq = WriteRequest().add(Write().deleteFeatureById(collection, feature.id))
        executeWrite(deleteFeatureReq)

        // And:
        Naksha.cache.clear()
        val featureVersions = executeRead(ReadFeatures().apply {
            catalogId = collection.catalogId
            collectionId += collection.id
            featureIds += feature.id
            queryHistory = true
            queryDeleted = true
        }).features.filterNotNull()

        // Then:
        assertEquals(3, featureVersions.size)
        val retrievedCreatedFeature = featureVersions.find { it.properties.xyz.action == Action.CREATE }!!
        val retrievedUpdatedFeature = featureVersions.find { it.properties.xyz.action == Action.UPDATE }!!
        val retrievedDeletedFeature = featureVersions.find { it.properties.xyz.action == Action.DELETE }!!

        // And:
        assertNotNull(retrievedCreatedFeature.properties.xyz.uuid)
        assertEquals(retrievedCreatedFeature.properties.xyz.nuuid, retrievedUpdatedFeature.properties.xyz.uuid)

        // And:
        assertEquals(retrievedUpdatedFeature.properties.xyz.nuuid, retrievedDeletedFeature.properties.xyz.uuid)

        // And:
        assertEquals(retrievedDeletedFeature.properties.xyz.nuuid, retrievedDeletedFeature.properties.xyz.uuid)
    }

    // TODO: make it pass as part of CASL-1094
    //@Test
    fun shouldFormCorrectUuidSequenceOnUpsert(){
        // Given:
        val feature = RandomFeatures.randomFeature().apply { title = "initial_version" }

        // When:
        val createFeatureReq = WriteRequest().add(Write().createFeature(collection, feature))
        val createdFeature = executeWrite(createFeatureReq).features.first()!!

        // And:
        val upsertFeatureReq = WriteRequest().add(Write().upsertFeature(collection, createdFeature.apply {
            title = "updated_version"
        }))
        executeWrite(upsertFeatureReq)

        // And:
        val deleteFeatureReq = WriteRequest().add(Write().deleteFeatureById(collection, feature.id))
        executeWrite(deleteFeatureReq)

        // And:
        Naksha.cache.clear()
        val featureVersions = executeRead(ReadFeatures().apply {
            catalogId = collection.catalogId
            collectionId += collection.id
            featureIds += feature.id
            queryHistory = true
            queryDeleted = true
        }).features.filterNotNull()

        // Then:
        assertEquals(3, featureVersions.size)
        val retrievedCreatedFeature = featureVersions.find { it.properties.xyz.action == Action.CREATE }!!
        val retrievedUpsertedFeature = featureVersions.find { it.properties.xyz.action == Action.UPDATE }!!
        val retrievedDeletedFeature = featureVersions.find { it.properties.xyz.action == Action.DELETE }!!

        // And:
        assertNotNull(retrievedCreatedFeature.properties.xyz.uuid)
        assertEquals(retrievedCreatedFeature.properties.xyz.nuuid, retrievedUpsertedFeature.properties.xyz.uuid) // TODO: FAILS

        // And:
        assertEquals(retrievedUpsertedFeature.properties.xyz.nuuid, retrievedDeletedFeature.properties.xyz.uuid)

        // And:
        assertEquals(retrievedDeletedFeature.properties.xyz.nuuid, retrievedDeletedFeature.properties.xyz.uuid)
    }
}