package naksha.psql

import naksha.model.SessionOptions
import naksha.model.XyzNs
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.request.ReadFeatures
import naksha.model.request.SuccessResponse
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.model.request.query.*
import naksha.psql.base.PgTestBase
import naksha.psql.util.ProxyFeatureGenerator
import naksha.psql.util.ProxyFeatureGenerator.generateRandomFeature
import kotlin.test.*

class ReadFeaturesByMetadataTest : PgTestBase(NakshaCollection("read_by_meta")) {
    
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
        val inputFeature = generateRandomFeature(featureId = TEST_FEATURE_ID)

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
        val inputFeature = generateRandomFeature(featureId = TEST_FEATURE_ID)

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
        val inputFeature = generateRandomFeature(featureId = TEST_FEATURE_ID)

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
        val inputFeature = generateRandomFeature(featureId = TEST_FEATURE_ID)

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
        val inputFeature = generateRandomFeature(featureId = TEST_FEATURE_ID)

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
        val inputFeature = generateRandomFeature(featureId = TEST_FEATURE_ID)

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
        val inputFeature = generateRandomFeature(featureId = TEST_FEATURE_ID).apply {
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
        val inputFeature = generateRandomFeature(featureId = TEST_FEATURE_ID).apply {
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
        val featureToCreate = generateRandomFeature(featureId = TEST_FEATURE_ID)
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
        val inputFeature = generateRandomFeature(featureId = TEST_FEATURE_ID)

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
        val inputFeature = generateRandomFeature(featureId = TEST_FEATURE_ID)

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
        val inputFeature = generateRandomFeature(featureId = TEST_FEATURE_ID).apply {
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
        val inputFeature = generateRandomFeature(featureId = TEST_FEATURE_ID).apply {
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
        val inputFeature = generateRandomFeature(featureId = TEST_FEATURE_ID)

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
        val featuresToCreate = ProxyFeatureGenerator.generateRandomFeatures(count = 10)

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