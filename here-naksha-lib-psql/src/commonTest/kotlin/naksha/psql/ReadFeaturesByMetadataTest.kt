package naksha.psql

import naksha.base.Action
import naksha.model.SessionOptions
import naksha.model.XyzNs
import naksha.model.objects.NakshaFeature
import naksha.model.objects.StandardMembers
import naksha.model.objects.XyzMembers
import naksha.model.request.*
import naksha.model.request.ops.*
import naksha.model.RandomFeatures
import naksha.model.RandomFeatures.RandomFeatures_C.randomFeature
import kotlin.test.*

class ReadFeaturesByMetadataTest : PgTestBase(collection = null, mapId = "") {
    
    companion object {
        private const val TEST_FEATURE_ID = "read_by_meta_test_feature"
    }

    @Test
    fun readFeatureByAppId() {
        testWithCollection("readFeatureByAppId")

        // Given:
        val inputFeature = randomFeature(featureId = TEST_FEATURE_ID)

        // And:
        val sessionOptions = SessionOptions(appId = "test_app_id_read_metadata")

        // When:
        insertFeature(feature = inputFeature, sessionOptions = sessionOptions)

        // And:
        val featuresByAppId = executeMetaQuery(
            Equals(XyzMembers.XyzAppId, sessionOptions.appId)
        ).features

        // Then:
        assertEquals(1, featuresByAppId.size)
        assertEquals(inputFeature.id, featuresByAppId[0]!!.id)
    }

    @Test
    fun readFeatureStartingWithAppId() {
        testWithCollection("readFeatureStartingWithAppId")

        // Given:
        val inputFeature = randomFeature(featureId = TEST_FEATURE_ID)

        // And:
        val sessionOptions = SessionOptions(appId = "prefixed_test_app_id_read_metadata")

        // When:
        insertFeature(feature = inputFeature, sessionOptions = sessionOptions)

        // And:
        val featuresByAppIdPrefix = executeMetaQuery(
            StartsWith(XyzMembers.XyzAppId, "prefixed_test_app")
        ).features

        // Then:
        assertEquals(1, featuresByAppIdPrefix.size)
        assertEquals(inputFeature.id, featuresByAppIdPrefix[0]!!.id)
    }

    @Test
    fun readFeatureByAuthor() {
        testWithCollection("readFeatureByAuthor")

        // Given:
        val inputFeature = randomFeature(featureId = TEST_FEATURE_ID)

        // And:
        val sessionOptions = SessionOptions(author = "John Doe")

        // When:
        insertFeature(feature = inputFeature, sessionOptions = sessionOptions)

        // And:
        val featuresByAuthor = executeMetaQuery(
            Equals(XyzMembers.XyzAuthor, sessionOptions.author)
        ).features

        // Then:
        assertEquals(1, featuresByAuthor.size)
        assertEquals(inputFeature.id, featuresByAuthor[0]!!.id)
    }

    @Test
    fun readFeatureStartingWithAuthor() {
        testWithCollection("readFeatureStartingWithAuthor")

        // Given:
        val inputFeature = randomFeature(featureId = TEST_FEATURE_ID)

        // And:
        val sessionOptions = SessionOptions(author = "Jacky Foo")

        // When:
        insertFeature(feature = inputFeature, sessionOptions = sessionOptions)

        // And:
        val featuresByAuthorPrefix = executeMetaQuery(
            StartsWith(XyzMembers.XyzAuthor, "Jacky")
        ).features

        // Then:
        assertEquals(1, featuresByAuthorPrefix.size)
        assertEquals(inputFeature.id, featuresByAuthorPrefix[0]!!.id)
    }

    @Test
    fun readFeatureById() {
        testWithCollection("readFeatureById")

        // Given:
        val inputFeature = randomFeature(featureId = TEST_FEATURE_ID)

        // When:
        insertFeature(feature = inputFeature)

        // And:
        val featuresById = executeMetaQuery(
            Equals(StandardMembers.Id, inputFeature.id)
        ).features

        // Then:
        assertEquals(1, featuresById.size)
        assertEquals(inputFeature.id, featuresById[0]!!.id)
    }

    @Test
    fun readFeatureStartingWithId() {
        testWithCollection("readFeatureStartingWithId")

        // Given:
        val inputFeature = randomFeature(featureId = TEST_FEATURE_ID)

        // When:
        insertFeature(feature = inputFeature)

        // And:
        val featuresByIdPrefix = executeMetaQuery(
            StartsWith(StandardMembers.Id, TEST_FEATURE_ID.substring(0..4))
        ).features

        // Then:
        assertEquals(1, featuresByIdPrefix.size)
        assertEquals(inputFeature.id, featuresByIdPrefix[0]!!.id)
    }

    @Test
    fun readFeatureByType() {
        testWithCollection("readFeatureByType")

        // Given:
        val inputFeature = randomFeature(featureId = TEST_FEATURE_ID).apply {
            featureType = "unusual_type"
        }

        // When:
        insertFeature(feature = inputFeature)

        // And:
        val featuresByType = executeMetaQuery(
            Equals(XyzMembers.XyzFeatureType, inputFeature.featureType)
        ).features

        // Then:
        assertEquals(1, featuresByType.size)
        assertEquals(inputFeature.id, featuresByType[0]!!.id)
    }

    @Test
    fun readFeatureStartingWithType() {
        testWithCollection("readFeatureStartingWithType")

        // Given:
        val inputFeature = randomFeature(featureId = TEST_FEATURE_ID).apply {
            featureType = "quite_unusual_type"
        }

        // When:
        insertFeature(feature = inputFeature)

        // And:
        val featuresByTypePrefix = executeMetaQuery(
            StartsWith(XyzMembers.XyzFeatureType, "quite")
        ).features

        // Then:
        assertEquals(1, featuresByTypePrefix.size)
        assertEquals(inputFeature.id, featuresByTypePrefix[0]!!.id)
    }

    @Test
    fun readFeatureByCombinedMetadata() {
        testWithCollection("readFeatureByCombinedMetadata")

        // Given: feature
        val appId = "some_app"
        val author = "some_author"
        val featureToCreate = randomFeature(featureId = TEST_FEATURE_ID)
        val writeFeaturesReq = WriteRequest().apply {
            add(Write().createFeature(collection, featureToCreate))
        }

        // When: executing feature write request with sepcific appId and author
        executeWrite(writeFeaturesReq, SessionOptions(appId = appId, author = author))

        // And: execute
        val featuresByAppIdAndAuthor = executeRead(ReadFeatures().apply {
            catalogId = collection.catalogId
            collectionId = collection.id
            queryMembers = And(
                Equals(XyzMembers.XyzAuthor, author),
                StartsWith(XyzMembers.XyzAppId, appId.substring(0, 2))
            )
        })

        // Then:
        assertEquals(1, featuresByAppIdAndAuthor.features.size)
        assertEquals(featureToCreate.id, featuresByAppIdAndAuthor.features[0]!!.id)
    }

    @Test
    fun readFeatureByCreatedAt(){
        testWithCollection("readFeatureByCreatedAt")

        // Given:
        val inputFeature = randomFeature(featureId = TEST_FEATURE_ID)

        // And:
        val insertedFeatureXyz = insertFeatureAndGetXyz(inputFeature)

        // And:
        val featuresByCreatedAt = executeMetaQuery(
            Equals(XyzMembers.XyzCreatedAt, insertedFeatureXyz.createdAt)
        )

        // Then:
        assertEquals(1, featuresByCreatedAt.features.size)
        assertEquals(inputFeature.id, featuresByCreatedAt.features[0]!!.id)
    }

    @Test
    fun readFeatureByUpdatedAt(){
        testWithCollection("readFeatureByUpdatedAt")

        // Given:
        val inputFeature = randomFeature(featureId = TEST_FEATURE_ID)

        // And:
        val insertedFeatureXyz = insertFeatureAndGetXyz(inputFeature)

        // And:
        val featuresByUpdatedAt = executeMetaQuery(
            Equals(XyzMembers.XyzUpdatedAt, insertedFeatureXyz.updatedAt)
        )

        // Then:
        assertEquals(1, featuresByUpdatedAt.features.size)
        assertEquals(inputFeature.id, featuresByUpdatedAt.features[0]!!.id)
    }

    @Test
    fun readFeatureByCreatedInTimeFrame(){
        testWithCollection("readFeatureByCreatedInTimeFrame")

        // Given:
        val inputFeature = randomFeature(featureId = TEST_FEATURE_ID).apply {
            type = "type_for_created_at_frame_test"
        }

        // And:
        val insertedFeatureXyz = insertFeatureAndGetXyz(inputFeature)

        // And:
        val featuresCreatedInFrame = executeMetaQuery(
            And(
                Gt(XyzMembers.XyzCreatedAt, insertedFeatureXyz.createdAt - 100),
                Lt(XyzMembers.XyzCreatedAt, insertedFeatureXyz.createdAt + 100)
            )
        )

        // Then:
        assertEquals(1, featuresCreatedInFrame.features.size)
        assertEquals(inputFeature.id, featuresCreatedInFrame.features[0]!!.id)
    }

    @Test
    fun readFeatureByUpdatedInTimeFrame(){
        testWithCollection("readFeatureByUpdatedInTimeFrame")

        // Given:
        val inputFeature = randomFeature(featureId = TEST_FEATURE_ID).apply {
            type = "type_for_updated_at_frame_test"
        }

        // And:
        val insertedFeatureXyz = insertFeatureAndGetXyz(inputFeature)

        // And:
        val featuresUpdatedInFrame = executeMetaQuery(
            And(
                Gte(XyzMembers.XyzUpdatedAt, insertedFeatureXyz.updatedAt),
                Lte(XyzMembers.XyzUpdatedAt, insertedFeatureXyz.updatedAt + 100)
            )
        )

        // Then:
        assertEquals(1, featuresUpdatedInFrame.features.size)
        assertEquals(inputFeature.id, featuresUpdatedInFrame.features[0]!!.id)
    }

    @Test
    fun readFeatureByAuthorTs(){
        testWithCollection("readFeatureByAuthorTs")

        // Given:
        val inputFeature = randomFeature(featureId = TEST_FEATURE_ID)

        // And:
        val insertedFeatureXyz = insertFeatureAndGetXyz(inputFeature)

        // And:
        val featuresByAuthorTs = executeMetaQuery(
            Equals(XyzMembers.XyzAuthorTimestamp, insertedFeatureXyz.authorTs)
        )

        // Then:
        assertEquals(1, featuresByAuthorTs.features.size)
        assertEquals(inputFeature.id, featuresByAuthorTs.features[0]!!.id)
    }

    @Test
    fun readFeatureByMetadataAlternative() {
        testWithCollection("readFeatureByMetadataAlternative")

        // Given
        val appId = "some_app"
        val featuresToCreate = RandomFeatures.randomFeatures(count = 10)

        // When
        insertFeatures(featuresToCreate, SessionOptions(appId = appId))

        // And: execute
        val featuresByAppIdAndAuthor = executeRead(ReadFeatures().apply {
            catalogId = collection.catalogId
            collectionId = collection.id
            queryMembers = Or(
                Equals(XyzMembers.XyzAuthor, "this_is_totally_off"),
                StartsWith(XyzMembers.XyzAppId, appId.substring(0, 2))
            )
        }).features

        // Then:
        assertEquals(10, featuresByAppIdAndAuthor.size)
        assertTrue(featuresByAppIdAndAuthor.map { it!!.id }
            .containsAll(featuresToCreate.map { it.id }))
    }

    @Test
    fun readFeaturesByAction(){
        testWithCollection("readFeaturesByAction")

        // Given
        val feature = randomFeature(featureId = TEST_FEATURE_ID)

        // When: feature is created
        insertFeatures(feature)

        // And: feature is deleted
        val deleteFeature = WriteRequest().add(Write().deleteFeatureById(collection, feature.id))
        executeWrite(deleteFeature)

        // And: History table is queried for everything besides CREATED
        val getHistoryWithoutUpdates = ReadFeatures().apply {
            catalogId = collection.catalogId
            collectionId = collection.id
            queryHistory = true
            queryDeleted = true
            queryMembers = Not(Equals(StandardMembers.Action, Action.CREATE.intValue))
        }
        val response = executeRead(getHistoryWithoutUpdates)
        val retrievedFeatures = response.features

        // Then: We only got DELETED state
        assertEquals(1, retrievedFeatures.size)
        val singleRetrievedHistoryFeature = retrievedFeatures[0]!!
        assertEquals(Action.DELETE, singleRetrievedHistoryFeature.properties.xyz.action)
    }

    private fun insertFeatureAndGetXyz(feature: NakshaFeature): XyzNs {
        insertFeature(feature = feature)
        val persistedFeatureResponse =  executeRead(ReadFeatures().apply {
            catalogId = collection.catalogId
            collectionId = collection.id
            featureIds += feature.id
        })
        val persistedFeatures = persistedFeatureResponse.features
        assertEquals(1, persistedFeatures.size)
        val persistedFeature = persistedFeatures[0]
        assertNotNull(persistedFeature)
        return persistedFeature.properties.xyz
    }

    private fun executeMetaQuery(op: Op): SuccessResponse {
        return executeRead(ReadFeatures().apply {
            catalogId = collection.catalogId
            collectionId = collection.id
            queryMembers = op
        })
    }
}