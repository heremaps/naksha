package naksha.psql

import naksha.model.Action
import naksha.model.SessionOptions
import naksha.model.XyzNs
import naksha.model.objects.NakshaFeature
import naksha.model.request.*
import naksha.model.request.query.*
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
            MemberQuery(
                column = MetaColumn.appId(),
                op = StringOp.EQUALS,
                value = sessionOptions.appId
            )
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
            MemberQuery(
                column = MetaColumn.appId(),
                op = StringOp.STARTS_WITH,
                value = "prefixed_test_app"
            )
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
            MemberQuery(
                column = MetaColumn.author(),
                op = StringOp.EQUALS,
                value = sessionOptions.author
            )
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
            MemberQuery(
                column = MetaColumn.author(),
                op = StringOp.STARTS_WITH,
                value = "Jacky"
            )
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
            MemberQuery(
                column = MetaColumn.id(),
                op = StringOp.EQUALS,
                value = inputFeature.id
            )
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
            MemberQuery(
                column = MetaColumn.id(),
                op = StringOp.STARTS_WITH,
                value = TEST_FEATURE_ID.substring(0..4)
            )
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
            MemberQuery(MetaColumn.featureType(), StringOp.EQUALS, inputFeature.featureType)
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
            type = "quite_unusual_type"
        }

        // When:
        insertFeature(feature = inputFeature)

        // And:
        val featuresByTypePrefix = executeMetaQuery(
            MemberQuery(MetaColumn.featureType(), StringOp.STARTS_WITH, "quite")
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
            mapId = collection.catalogId
            collectionIds += collection.id
            query.members = MemberAnd(
                MemberQuery(MetaColumn.author(), StringOp.EQUALS, author),
                MemberQuery(MetaColumn.appId(), StringOp.STARTS_WITH, appId.substring(0, 2))
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
        val featuresByCreatedAt = executeMetaQuery(MemberQuery(
            MetaColumn.createdAt(),
            DoubleOp.EQ,
            insertedFeatureXyz.createdAt
        ))

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
        val featuresByUpdatedAt = executeMetaQuery(MemberQuery(
            MetaColumn.updatedAt(),
            DoubleOp.EQ,
            insertedFeatureXyz.updatedAt
        ))

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
            MemberAnd(
                MemberQuery(
                    MetaColumn.createdAt(),
                    DoubleOp.GT,
                    insertedFeatureXyz.createdAt - 100
                ),
                MemberQuery(
                    MetaColumn.createdAt(),
                    DoubleOp.LT,
                    insertedFeatureXyz.createdAt + 100
                )
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
            MemberAnd(
                MemberQuery(
                    MetaColumn.updatedAt(),
                    DoubleOp.GTE,
                    insertedFeatureXyz.updatedAt
                ),
                MemberQuery(
                    MetaColumn.updatedAt(),
                    DoubleOp.LTE,
                    insertedFeatureXyz.updatedAt + 100
                )
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
        val featuresByAuthorTs = executeMetaQuery(MemberQuery(
            MetaColumn.authorTs(),
            DoubleOp.EQ,
            insertedFeatureXyz.authorTs
        ))

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
            mapId = collection.catalogId
            collectionIds += collection.id
            query.members = MemberOr(
                MemberQuery(MetaColumn.author(), StringOp.EQUALS, "this_is_totally_off"),
                MemberQuery(MetaColumn.appId(), StringOp.STARTS_WITH, appId.substring(0, 2))
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
            mapId = collection.catalogId
            collectionIds += collection.id
            queryHistory = true
            queryDeleted = true
            query = RequestQuery().apply {
                members = MemberQuery(MetaColumn.action(), DoubleOp.NE, Action.CREATE.intValue)
            }
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
            mapId = collection.catalogId
            collectionIds += collection.id
            featureIds += feature.id
        })
        val persistedFeatures = persistedFeatureResponse.features
        assertEquals(1, persistedFeatures.size)
        val persistedFeature = persistedFeatures[0]
        assertNotNull(persistedFeature)
        return persistedFeature.properties.xyz
    }

    private fun executeMetaQuery(metaQuery: IMemberQuery): SuccessResponse {
        return executeRead(ReadFeatures().apply {
            mapId = collection.catalogId
            collectionIds += collection.id
            query.members = metaQuery
        })
    }
}