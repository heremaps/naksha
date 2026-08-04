package naksha.psql

import naksha.base.Id

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

class ReadFeaturesByMetadataTest : PgTestBase(collection = null, catalogId = "") {
    
    companion object {
        private const val TEST_FEATURE_ID = "read_by_meta_test_feature"
    }

    @Test
    fun readFeatureByAppId() {
        testWithCollection("readFeatureByAppId")

        // Given:
        val inputFeature = randomFeature(featureId = Id(TEST_FEATURE_ID))

        // And:
        val sessionOptions = SessionOptions(storage.defaultDatabaseId, appId = "test_app_id_read_metadata")

        // When:
        insertFeature(feature = inputFeature, sessionOptions = sessionOptions)

        // And:
        val featuresByAppId = executeMetaQuery(
            Equals(XyzMembers.XyzAppId, sessionOptions.appId)
        ).asFeatures

        // Then:
        assertEquals(1, featuresByAppId.size)
        assertEquals(inputFeature.id, featuresByAppId[0]!!.id)
    }

    @Test
    fun readFeatureStartingWithAppId() {
        testWithCollection("readFeatureStartingWithAppId")

        // Given:
        val inputFeature = randomFeature(featureId = Id(TEST_FEATURE_ID))

        // And:
        val sessionOptions = SessionOptions(databaseId = storage.defaultDatabaseId, appId = "prefixed_test_app_id_read_metadata")

        // When:
        insertFeature(feature = inputFeature, sessionOptions = sessionOptions)

        // And:
        val featuresByAppIdPrefix = executeMetaQuery(
            StartsWith(XyzMembers.XyzAppId, "prefixed_test_app")
        ).asFeatures

        // Then:
        assertEquals(1, featuresByAppIdPrefix.size)
        assertEquals(inputFeature.id, featuresByAppIdPrefix[0]!!.id)
    }

    @Test
    fun readFeatureByAuthor() {
        testWithCollection("readFeatureByAuthor")

        // Given:
        val inputFeature = randomFeature(featureId = Id(TEST_FEATURE_ID))

        // And:
        val sessionOptions = SessionOptions(databaseId = storage.defaultDatabaseId, author = "John Doe")

        // When:
        insertFeature(feature = inputFeature, sessionOptions = sessionOptions)

        // And:
        val featuresByAuthor = executeMetaQuery(
            Equals(XyzMembers.XyzAuthor, sessionOptions.author)
        ).asFeatures

        // Then:
        assertEquals(1, featuresByAuthor.size)
        assertEquals(inputFeature.id, featuresByAuthor[0]!!.id)
    }

    @Test
    fun readFeatureStartingWithAuthor() {
        testWithCollection("readFeatureStartingWithAuthor")

        // Given:
        val inputFeature = randomFeature(featureId = Id(TEST_FEATURE_ID))

        // And:
        val sessionOptions = SessionOptions(databaseId = storage.defaultDatabaseId, author = "Jacky Foo")

        // When:
        insertFeature(feature = inputFeature, sessionOptions = sessionOptions)

        // And:
        val featuresByAuthorPrefix = executeMetaQuery(
            StartsWith(XyzMembers.XyzAuthor, "Jacky")
        ).asFeatures

        // Then:
        assertEquals(1, featuresByAuthorPrefix.size)
        assertEquals(inputFeature.id, featuresByAuthorPrefix[0]!!.id)
    }

    @Test
    fun readFeatureById() {
        testWithCollection("readFeatureById")

        // Given:
        val inputFeature = randomFeature(featureId = Id(TEST_FEATURE_ID))

        // When:
        insertFeature(feature = inputFeature)

        // And:
        val featuresById = executeMetaQuery(
            Equals(StandardMembers.IdMember, inputFeature.id)
        ).asFeatures

        // Then:
        assertEquals(1, featuresById.size)
        assertEquals(inputFeature.id, featuresById[0]!!.id)
    }

    @Test
    fun readFeatureStartingWithId() {
        testWithCollection("readFeatureStartingWithId")

        // Given:
        val inputFeature = randomFeature(featureId = Id(TEST_FEATURE_ID))

        // When:
        insertFeature(feature = inputFeature)

        // And:
        val featuresByIdPrefix = executeMetaQuery(
            StartsWith(StandardMembers.IdMember, TEST_FEATURE_ID.substring(0..4))
        ).asFeatures

        // Then:
        assertEquals(1, featuresByIdPrefix.size)
        assertEquals(inputFeature.id, featuresByIdPrefix[0]!!.id)
    }

    @Test
    fun readFeatureByType() {
        testWithCollection("readFeatureByType")

        // Given:
        val inputFeature = randomFeature(featureId = Id(TEST_FEATURE_ID)).apply {
            momType = "unusual_type"
        }

        // When:
        insertFeature(feature = inputFeature)

        // And:
        val featuresByType = executeMetaQuery(
            Equals(XyzMembers.XyzFeatureType, inputFeature.momType)
        ).asFeatures

        // Then:
        assertEquals(1, featuresByType.size)
        assertEquals(inputFeature.id, featuresByType[0]!!.id)
    }

    @Test
    fun readFeatureStartingWithType() {
        testWithCollection("readFeatureStartingWithType")

        // Given:
        val inputFeature = randomFeature(featureId = Id(TEST_FEATURE_ID)).apply {
            momType = "quite_unusual_type"
        }

        // When:
        insertFeature(feature = inputFeature)

        // And:
        val featuresByTypePrefix = executeMetaQuery(
            StartsWith(XyzMembers.XyzFeatureType, "quite")
        ).asFeatures

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
        val featureToCreate = randomFeature(featureId = Id(TEST_FEATURE_ID))
        val writeFeaturesReq = WriteRequest().apply {
            add(Write().createFeature(collection, featureToCreate))
        }

        // When: executing feature write request with sepcific appId and author
        executeWriteAndLoadTuples(writeFeaturesReq, SessionOptions(databaseId = storage.defaultDatabaseId, appId = appId, author = author))

        // And: execute
        val featuresByAppIdAndAuthor = executeReadAndLoadTuple(ReadFeatures().apply {
            catalogId = collection.catalogId
            collectionId = collection.id
            memberQuery = And(
                Equals(XyzMembers.XyzAuthor, author),
                StartsWith(XyzMembers.XyzAppId, appId.substring(0, 2))
            )
        })

        // Then:
        assertEquals(1, featuresByAppIdAndAuthor.asFeatures.size)
        assertEquals(featureToCreate.id, featuresByAppIdAndAuthor.asFeatures[0]!!.id)
    }

    @Test
    fun readFeatureByCreatedAt(){
        testWithCollection("readFeatureByCreatedAt")

        // Given:
        val inputFeature = randomFeature(featureId = Id(TEST_FEATURE_ID))

        // And:
        val insertedFeatureXyz = insertFeatureAndGetXyz(inputFeature)

        // And:
        val featuresByCreatedAt = executeMetaQuery(
            Equals(XyzMembers.XyzCreatedAt, insertedFeatureXyz.createdAt)
        )

        // Then:
        assertEquals(1, featuresByCreatedAt.asFeatures.size)
        assertEquals(inputFeature.id, featuresByCreatedAt.asFeatures[0]!!.id)
    }

    @Test
    fun readFeatureByUpdatedAt(){
        testWithCollection("readFeatureByUpdatedAt")

        // Given:
        val inputFeature = randomFeature(featureId = Id(TEST_FEATURE_ID))

        // And:
        val insertedFeatureXyz = insertFeatureAndGetXyz(inputFeature)

        // And:
        val featuresByUpdatedAt = executeMetaQuery(
            Equals(XyzMembers.XyzUpdatedAt, insertedFeatureXyz.updatedAt)
        )

        // Then:
        assertEquals(1, featuresByUpdatedAt.asFeatures.size)
        assertEquals(inputFeature.id, featuresByUpdatedAt.asFeatures[0]!!.id)
    }

    @Test
    fun readFeatureByCreatedInTimeFrame(){
        testWithCollection("readFeatureByCreatedInTimeFrame")

        // Given:
        val inputFeature = randomFeature(featureId = Id(TEST_FEATURE_ID)).apply {
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
        assertEquals(1, featuresCreatedInFrame.asFeatures.size)
        assertEquals(inputFeature.id, featuresCreatedInFrame.asFeatures[0]!!.id)
    }

    @Test
    fun readFeatureByUpdatedInTimeFrame(){
        testWithCollection("readFeatureByUpdatedInTimeFrame")

        // Given:
        val inputFeature = randomFeature(featureId = Id(TEST_FEATURE_ID)).apply {
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
        assertEquals(1, featuresUpdatedInFrame.asFeatures.size)
        assertEquals(inputFeature.id, featuresUpdatedInFrame.asFeatures[0]!!.id)
    }

    @Test
    fun readFeatureByAuthorTs(){
        testWithCollection("readFeatureByAuthorTs")

        // Given:
        val inputFeature = randomFeature(featureId = Id(TEST_FEATURE_ID))

        // And:
        val insertedFeatureXyz = insertFeatureAndGetXyz(inputFeature)

        // And:
        val featuresByAuthorTs = executeMetaQuery(
            Equals(XyzMembers.XyzAuthorTimestamp, insertedFeatureXyz.authorTs)
        )

        // Then:
        assertEquals(1, featuresByAuthorTs.asFeatures.size)
        assertEquals(inputFeature.id, featuresByAuthorTs.asFeatures[0]!!.id)
    }

    @Test
    fun readFeatureByMetadataAlternative() {
        testWithCollection("readFeatureByMetadataAlternative")

        // Given
        val appId = "some_app"
        val featuresToCreate = RandomFeatures.randomFeatures(count = 10)

        // When
        insertFeatures(featuresToCreate, SessionOptions(databaseId = storage.defaultDatabaseId, appId = appId))

        // And: execute
        val featuresByAppIdAndAuthor = executeReadAndLoadTuple(ReadFeatures().apply {
            catalogId = collection.catalogId
            collectionId = collection.id
            memberQuery = Or(
                Equals(XyzMembers.XyzAuthor, "this_is_totally_off"),
                StartsWith(XyzMembers.XyzAppId, appId.substring(0, 2))
            )
        }).asFeatures

        // Then:
        assertEquals(10, featuresByAppIdAndAuthor.size)
        assertTrue(featuresByAppIdAndAuthor.map { it!!.id }
            .containsAll(featuresToCreate.map { it.id }))
    }

    @Test
    fun readFeaturesByAction(){
        testWithCollection("readFeaturesByAction")

        // Given
        val feature = randomFeature(featureId = Id(TEST_FEATURE_ID))

        // When: feature is created
        insertFeatures(feature)

        // And: feature is deleted
        val deleteFeature = WriteRequest().add(Write().deleteFeatureById(collection, feature.id))
        executeWriteAndLoadTuples(deleteFeature)

        // And: History table is queried for everything besides CREATED
        val getHistoryWithoutUpdates = ReadFeatures().apply {
            catalogId = collection.catalogId
            collectionId = collection.id
            queryHistory = true
            queryDeleted = true
            memberQuery = Not(Equals(StandardMembers.ActionMember, Action.CREATE.intValue))
        }
        val response = executeReadAndLoadTuple(getHistoryWithoutUpdates)
        val retrievedFeatures = response.asFeatures

        // Then: We only got DELETED state
        assertEquals(1, retrievedFeatures.size)
        val singleRetrievedHistoryFeature = retrievedFeatures[0]!!
        assertEquals(Action.DELETE, singleRetrievedHistoryFeature.properties.xyz.action)
    }

    private fun insertFeatureAndGetXyz(feature: NakshaFeature): XyzNs {
        insertFeature(feature = feature)
        val persistedFeatureResponse =  executeReadAndLoadTuple(ReadFeatures().apply {
            catalogId = collection.catalogId
            collectionId = collection.id
            featureIds += feature.id
        })
        val persistedFeatures = persistedFeatureResponse.asFeatures
        assertEquals(1, persistedFeatures.size)
        val persistedFeature = persistedFeatures[0]
        assertNotNull(persistedFeature)
        return persistedFeature.properties.xyz
    }

    private fun executeMetaQuery(op: Op): SuccessResponse {
        return executeReadAndLoadTuple(ReadFeatures().apply {
            catalogId = collection.catalogId
            collectionId = collection.id
            memberQuery = op
        })
    }
}