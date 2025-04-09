package naksha.psql

import naksha.model.Action
import naksha.model.Operation
import naksha.model.SessionOptions
import naksha.model.XyzNs
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.request.*
import naksha.model.request.query.*
import naksha.psql.base.PgTestBase
import naksha.model.RandomFeatures
import naksha.model.RandomFeatures.RandomFeatures_C.randomFeature
import naksha.psql.PgTest.PgTest_C.TEST_MAP_ID
import kotlin.test.*

class ReadFeaturesByMetadataTest : PgTestBase(NakshaCollection("read_by_meta", TEST_MAP_ID)) {
    
    companion object {
        private const val TEST_FEATURE_ID = "read_by_meta_test_feature"
    }

    @AfterTest
    fun removeTestFeature(){
        val deleteReq = WriteRequest().add(Write().deleteFeatureById(collection, TEST_FEATURE_ID))
        assertSuccess(executeWrite(deleteReq))
    }
    
    @Test
    fun shouldReadFeatureByAppId() {
        // Given:
        val inputFeature = randomFeature(featureId = TEST_FEATURE_ID)

        // And:
        val sessionOptions = SessionOptions(appId = "test_app_id_read_metadata")

        // When:
        insertFeature(feature = inputFeature, sessionOptions = sessionOptions)

        // And:
        val featuresByAppId = executeMetaQuery(
            MetaQuery(
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
    fun shouldReadFeatureStartingWithAppId() {
        // Given:
        val inputFeature = randomFeature(featureId = TEST_FEATURE_ID)

        // And:
        val sessionOptions = SessionOptions(appId = "prefixed_test_app_id_read_metadata")

        // When:
        insertFeature(feature = inputFeature, sessionOptions = sessionOptions)

        // And:
        val featuresByAppIdPrefix = executeMetaQuery(
            MetaQuery(
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
    fun shouldReadFeatureByAuthor() {
        // Given:
        val inputFeature = randomFeature(featureId = TEST_FEATURE_ID)

        // And:
        val sessionOptions = SessionOptions(author = "John Doe")

        // When:
        insertFeature(feature = inputFeature, sessionOptions = sessionOptions)

        // And:
        val featuresByAuthor = executeMetaQuery(
            MetaQuery(
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
    fun shouldReadFeatureStartingWithAuthor() {
        // Given:
        val inputFeature = randomFeature(featureId = TEST_FEATURE_ID)

        // And:
        val sessionOptions = SessionOptions(author = "Jacky Foo")

        // When:
        insertFeature(feature = inputFeature, sessionOptions = sessionOptions)

        // And:
        val featuresByAuthorPrefix = executeMetaQuery(
            MetaQuery(
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
    fun shouldReadFeatureById() {
        // Given:
        val inputFeature = randomFeature(featureId = TEST_FEATURE_ID)

        // When:
        insertFeature(feature = inputFeature)

        // And:
        val featuresById = executeMetaQuery(
            MetaQuery(
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
    fun shouldReadFeatureStartingWithId() {
        // Given:
        val inputFeature = randomFeature(featureId = TEST_FEATURE_ID)

        // When:
        insertFeature(feature = inputFeature)

        // And:
        val featuresByIdPrefix = executeMetaQuery(
            MetaQuery(
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
    fun shouldReadFeatureByType() {
        // Given:
        val inputFeature = randomFeature(featureId = TEST_FEATURE_ID).apply {
            featureType = "unusual_type"
        }

        // When:
        insertFeature(feature = inputFeature)

        // And:
        val featuresByType = executeMetaQuery(
            MetaQuery(MetaColumn.featureType(), StringOp.EQUALS, inputFeature.featureType)
        ).features

        // Then:
        assertEquals(1, featuresByType.size)
        assertEquals(inputFeature.id, featuresByType[0]!!.id)
    }

    @Test
    fun shouldReadFeatureStartingWithType() {
        // Given:
        val inputFeature = randomFeature(featureId = TEST_FEATURE_ID).apply {
            type = "quite_unusual_type"
        }

        // When:
        insertFeature(feature = inputFeature)

        // And:
        val featuresByTypePrefix = executeMetaQuery(
            MetaQuery(MetaColumn.featureType(), StringOp.STARTS_WITH, "quite")
        ).features

        // Then:
        assertEquals(1, featuresByTypePrefix.size)
        assertEquals(inputFeature.id, featuresByTypePrefix[0]!!.id)
    }

    @Test
    fun shouldReadFeatureByCombinedMetadata() {
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
            collectionIds += collection.id
            query.metadata = MetaAnd(
                MetaQuery(MetaColumn.author(), StringOp.EQUALS, author),
                MetaQuery(MetaColumn.appId(), StringOp.STARTS_WITH, appId.substring(0, 2))
            )
        })

        // Then:
        assertEquals(1, featuresByAppIdAndAuthor.features.size)
        assertEquals(featureToCreate.id, featuresByAppIdAndAuthor.features[0]!!.id)
    }

    @Test
    fun shouldReadFeatureByCreatedAt(){
        // Given:
        val inputFeature = randomFeature(featureId = TEST_FEATURE_ID)

        // And:
        val insertedFeatureXyz = insertFeatureAndGetXyz(inputFeature)

        // And:
        val featuresByCreatedAt = executeMetaQuery(MetaQuery(
            MetaColumn.createdAt(),
            DoubleOp.EQ,
            insertedFeatureXyz.createdAt
        ))

        // Then:
        assertEquals(1, featuresByCreatedAt.features.size)
        assertEquals(inputFeature.id, featuresByCreatedAt.features[0]!!.id)
    }

    @Test
    fun shouldReadFeatureByUpdatedAt(){
        // Given:
        val inputFeature = randomFeature(featureId = TEST_FEATURE_ID)

        // And:
        val insertedFeatureXyz = insertFeatureAndGetXyz(inputFeature)

        // And:
        val featuresByUpdatedAt = executeMetaQuery(MetaQuery(
            MetaColumn.updatedAt(),
            DoubleOp.EQ,
            insertedFeatureXyz.updatedAt
        ))

        // Then:
        assertEquals(1, featuresByUpdatedAt.features.size)
        assertEquals(inputFeature.id, featuresByUpdatedAt.features[0]!!.id)
    }

    @Test
    fun shouldReadFeatureByCreatedInTimeFrame(){
        // Given:
        val inputFeature = randomFeature(featureId = TEST_FEATURE_ID).apply {
            type = "type_for_created_at_frame_test"
        }

        // And:
        val insertedFeatureXyz = insertFeatureAndGetXyz(inputFeature)

        // And:
        val featuresCreatedInFrame = executeMetaQuery(
            MetaAnd(
                MetaQuery(
                    MetaColumn.createdAt(),
                    DoubleOp.GT,
                    insertedFeatureXyz.createdAt - 100
                ),
                MetaQuery(
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
    fun shouldReadFeatureByUpdatedInTimeFrame(){
        // Given:
        val inputFeature = randomFeature(featureId = TEST_FEATURE_ID).apply {
            type = "type_for_updated_at_frame_test"
        }

        // And:
        val insertedFeatureXyz = insertFeatureAndGetXyz(inputFeature)

        // And:
        val featuresUpdatedInFrame = executeMetaQuery(
            MetaAnd(
                MetaQuery(
                    MetaColumn.updatedAt(),
                    DoubleOp.GTE,
                    insertedFeatureXyz.updatedAt
                ),
                MetaQuery(
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
    fun shouldReadFeatureByAuthorTs(){
        // Given:
        val inputFeature = randomFeature(featureId = TEST_FEATURE_ID)

        // And:
        val insertedFeatureXyz = insertFeatureAndGetXyz(inputFeature)

        // And:
        val featuresByAuthorTs = executeMetaQuery(MetaQuery(
            MetaColumn.authorTs(),
            DoubleOp.EQ,
            insertedFeatureXyz.authorTs
        ))

        // Then:
        assertEquals(1, featuresByAuthorTs.features.size)
        assertEquals(inputFeature.id, featuresByAuthorTs.features[0]!!.id)
    }

    @Test
    fun shouldReadFeatureByMetadataAlternative() {
        // Given
        val appId = "some_app"
        val featuresToCreate = RandomFeatures.randomFeatures(count = 10)

        // When
        insertFeatures(featuresToCreate, SessionOptions(appId = appId))

        // And: execute
        val featuresByAppIdAndAuthor = executeRead(ReadFeatures().apply {
            collectionIds += collection.id
            query.metadata = MetaOr(
                MetaQuery(MetaColumn.author(), StringOp.EQUALS, "this_is_totally_off"),
                MetaQuery(MetaColumn.appId(), StringOp.STARTS_WITH, appId.substring(0, 2))
            )
        }).features

        // Then:
        assertEquals(10, featuresByAppIdAndAuthor.size)
        assertTrue(featuresByAppIdAndAuthor.map { it!!.id }
            .containsAll(featuresToCreate.map { it.id }))
    }

    @Test
    fun shouldReadFeaturesByOperation(){
        // Given
        val feature = randomFeature(featureId = TEST_FEATURE_ID).apply {
            title = "Title no 1"
        }

        // When: feature is created
        val creationResp = insertFeatures(feature)
        val createdFeature = creationResp.features[0] ?: fail("Expected non-empty creation response")

        // And: feature is modified
        val modifiedTitle = "Title no 2"
        val modifyFeature = WriteRequest().add(Write().updateFeature(
            collection,
            createdFeature.apply { title = modifiedTitle },
            true
        ))
        executeWrite(modifyFeature)

        // And: feature is deleted
        val deleteFeature = WriteRequest().add(Write().deleteFeatureById(collection, feature.id))
        executeWrite(deleteFeature)

        // And: Collection (with history & deleted tables) is queried for UPDATE
        val getHistoryWithoutUpdates = ReadFeatures().apply {
            collectionIds += collection.id
            queryHistory = true
            queryDeleted = true
            query = RequestQuery().apply {
                metadata = MetaQuery(MetaColumn.operation(), DoubleOp.EQ, Operation.UPDATED.intValue)
            }
        }
        val response = executeRead(getHistoryWithoutUpdates)
        val retrievedFeatures = response.features

        // Then: We only got UPDATED state - the one matching updated feature
        assertEquals(1, retrievedFeatures.size)
        val singleRetrievedHistoryFeature = retrievedFeatures[0]!!
        assertEquals(modifiedTitle, singleRetrievedHistoryFeature.title)
        assertEquals(Operation.UPDATED, singleRetrievedHistoryFeature.properties.xyz.operation)
    }

    @Test
    fun shouldReadFeaturesByAction(){
        // Given
        val feature = randomFeature(featureId = TEST_FEATURE_ID)

        // When: feature is created
        insertFeatures(feature)

        // And: feature is deleted
        val deleteFeature = WriteRequest().add(Write().deleteFeatureById(collection, feature.id))
        executeWrite(deleteFeature)

        // And: History table is queried for everything besides CREATED
        val getHistoryWithoutUpdates = ReadFeatures().apply {
            collectionIds += collection.id
            queryHistory = true
            queryDeleted = true
            query = RequestQuery().apply {
                metadata = MetaQuery(MetaColumn.action(), DoubleOp.NE, Action.CREATED.intValue)
            }
        }
        val response = executeRead(getHistoryWithoutUpdates)
        val retrievedFeatures = response.features

        // Then: We only got DELETED state
        assertEquals(1, retrievedFeatures.size)
        val singleRetrievedHistoryFeature = retrievedFeatures[0]!!
        assertEquals(Action.DELETED, singleRetrievedHistoryFeature.properties.xyz.action)
    }

    private fun insertFeatureAndGetXyz(feature: NakshaFeature): XyzNs {
        insertFeature(feature = feature)
        val persistedFeatureResponse =  executeRead(ReadFeatures().apply {
            collectionIds += collection.id
            featureIds += feature.id
        })
        val persistedFeatures = persistedFeatureResponse.features
        assertEquals(1, persistedFeatures.size)
        val persistedFeature = persistedFeatures[0]
        assertNotNull(persistedFeature)
        return persistedFeature.properties.xyz
    }

    private fun executeMetaQuery(metaQuery: IMetaQuery): SuccessResponse {
        return executeRead(ReadFeatures().apply {
            collectionIds += collection.id
            query.metadata = metaQuery
        })
    }
}