package naksha.psql

import naksha.model.Metadata
import naksha.model.SessionOptions
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.request.ReadFeatures
import naksha.model.request.SuccessResponse
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.model.request.query.*
import naksha.psql.base.PgTestBase
import naksha.psql.util.ProxyFeatureGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReadFeaturesByMetadataTest : PgTestBase(NakshaCollection("read_by_meta")) {

    @Test
    fun shouldReadFeatureByAppId() {
        // Given:
        val inputFeature = ProxyFeatureGenerator.generateRandomFeature()

        // And:
        val sessionOptions = SessionOptions(appId = "test_app_id_read_metadata")

        // When:
        insertFeature(feature = inputFeature, sessionOptions = sessionOptions)

        // And:
        val featuresByAppId = executeMetaQuery(
            MetaQuery(
                column = TupleColumn.appId(),
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
        val inputFeature = ProxyFeatureGenerator.generateRandomFeature()

        // And:
        val sessionOptions = SessionOptions(appId = "prefixed_test_app_id_read_metadata")

        // When:
        insertFeature(feature = inputFeature, sessionOptions = sessionOptions)

        // And:
        val featuresByAppIdPrefix = executeMetaQuery(
            MetaQuery(
                column = TupleColumn.appId(),
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
        val inputFeature = ProxyFeatureGenerator.generateRandomFeature()

        // And:
        val sessionOptions = SessionOptions(author = "John Doe")

        // When:
        insertFeature(feature = inputFeature, sessionOptions = sessionOptions)

        // And:
        val featuresByAuthor = executeMetaQuery(
            MetaQuery(
                column = TupleColumn.author(),
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
        val inputFeature = ProxyFeatureGenerator.generateRandomFeature()

        // And:
        val sessionOptions = SessionOptions(author = "Jacky Foo")

        // When:
        insertFeature(feature = inputFeature, sessionOptions = sessionOptions)

        // And:
        val featuresByAuthorPrefix = executeMetaQuery(
            MetaQuery(
                column = TupleColumn.author(),
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
        val inputFeature = ProxyFeatureGenerator.generateRandomFeature()

        // When:
        insertFeature(feature = inputFeature)

        // And:
        val featuresById = executeMetaQuery(
            MetaQuery(
                column = TupleColumn.id(),
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
        val inputFeature = ProxyFeatureGenerator.generateRandomFeature().apply {
            id = "very_random_id_says_hi"
        }

        // When:
        insertFeature(feature = inputFeature)

        // And:
        val featuresByIdPrefix = executeMetaQuery(
            MetaQuery(
                column = TupleColumn.id(),
                op = StringOp.STARTS_WITH,
                value = "very"
            )
        ).features

        // Then:
        assertEquals(1, featuresByIdPrefix.size)
        assertEquals(inputFeature.id, featuresByIdPrefix[0]!!.id)
    }

    @Test
    fun shouldReadFeatureByType() {
        // Given:
        val inputFeature = ProxyFeatureGenerator.generateRandomFeature().apply {
            type = "unusual_type"
        }

        // When:
        insertFeature(feature = inputFeature)

        // And:
        val featuresByType = executeMetaQuery(
            MetaQuery(
                column = TupleColumn.type(),
                op = StringOp.EQUALS,
                value = inputFeature.type
            )
        ).features

        // Then:
        assertEquals(1, featuresByType.size)
        assertEquals(inputFeature.id, featuresByType[0]!!.id)
    }

    @Test
    fun shouldReadFeatureStartingWithType() {
        // Given:
        val inputFeature = ProxyFeatureGenerator.generateRandomFeature().apply {
            type = "quite_unusual_type"
        }

        // When:
        insertFeature(feature = inputFeature)

        // And:
        val featuresByTypePrefix = executeMetaQuery(
            MetaQuery(
                column = TupleColumn.type(),
                op = StringOp.STARTS_WITH,
                value = "quite"
            )
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
        val featureToCreate = ProxyFeatureGenerator.generateRandomFeature()
        val writeFeaturesReq = WriteRequest().apply {
            add(Write().createFeature(null, collection!!.id, featureToCreate))
        }

        // When: executing feature write request with sepcific appId and author
        executeWrite(writeFeaturesReq, SessionOptions(appId = appId, author = author))

        // And: execute
        val featuresByAppIdAndAuthor = executeRead(ReadFeatures().apply {
            collectionIds += collection!!.id
            query.metadata = MetaAnd(
                MetaQuery(TupleColumn.author(), StringOp.EQUALS, author),
                MetaQuery(TupleColumn.appId(), StringOp.STARTS_WITH, appId.substring(0, 2))
            )
        })

        // Then:
        assertEquals(1, featuresByAppIdAndAuthor.features.size)
        assertEquals(featureToCreate.id, featuresByAppIdAndAuthor.features[0]!!.id)
    }

    @Test
    fun shouldReadFeatureByCreatedAt(){
        // Given:
        val inputFeature = ProxyFeatureGenerator.generateRandomFeature()

        // And:
        val insertedFeatureMeta = insertFeatureAndGetMeta(inputFeature)

        // And:
        val featuresByCreatedAt = executeMetaQuery(MetaQuery(
            TupleColumn.createdAt(),
            DoubleOp.EQ,
            insertedFeatureMeta.createdAt
        ))

        // Then:
        assertEquals(1, featuresByCreatedAt.features.size)
        assertEquals(inputFeature.id, featuresByCreatedAt.features[0]!!.id)
    }

    @Test
    fun shouldReadFeatureByUpdatedAt(){
        // Given:
        val inputFeature = ProxyFeatureGenerator.generateRandomFeature()

        // And:
        val insertedFeatureMeta = insertFeatureAndGetMeta(inputFeature)

        // And:
        val featuresByUpdatedAt = executeMetaQuery(MetaQuery(
            TupleColumn.updatedAt(),
            DoubleOp.EQ,
            insertedFeatureMeta.updatedAt
        ))

        // Then:
        assertEquals(1, featuresByUpdatedAt.features.size)
        assertEquals(inputFeature.id, featuresByUpdatedAt.features[0]!!.id)
    }

    @Test
    fun shouldReadFeatureByCreatedInTimeFrame(){
        // Given:
        val inputFeature = ProxyFeatureGenerator.generateRandomFeature().apply {
            type = "type_for_created_at_frame_test"
        }

        // And:
        val insertedFeatureMeta = insertFeatureAndGetMeta(inputFeature)

        // And:
        val featuresCreatedInFrame = executeMetaQuery(
            MetaAnd(
                MetaQuery(
                    TupleColumn.createdAt(),
                    DoubleOp.GT,
                    insertedFeatureMeta.createdAt - 100
                ),
                MetaQuery(
                    TupleColumn.createdAt(),
                    DoubleOp.LT,
                    insertedFeatureMeta.createdAt + 100
                ),
                MetaQuery(
                    TupleColumn.type(),
                    StringOp.EQUALS,
                    inputFeature.type
                )
            )
        );

        // Then:
        assertEquals(1, featuresCreatedInFrame.features.size)
        assertEquals(inputFeature.id, featuresCreatedInFrame.features[0]!!.id)
    }

    @Test
    fun shouldReadFeatureByUpdatedInTimeFrame(){
        // Given:
        val inputFeature = ProxyFeatureGenerator.generateRandomFeature().apply {
            type = "type_for_updated_at_frame_test"
        }

        // And:
        val insertedFeatureMeta = insertFeatureAndGetMeta(inputFeature)

        // And:
        val featuresUpdatedInFrame = executeMetaQuery(
            MetaAnd(
                MetaQuery(
                    TupleColumn.updatedAt(),
                    DoubleOp.GTE,
                    insertedFeatureMeta.updatedAt
                ),
                MetaQuery(
                    TupleColumn.updatedAt(),
                    DoubleOp.LTE,
                    insertedFeatureMeta.updatedAt + 100
                ),
                MetaQuery(
                    TupleColumn.type(),
                    StringOp.EQUALS,
                    inputFeature.type
                )
            )
        );

        // Then:
        assertEquals(1, featuresUpdatedInFrame.features.size)
        assertEquals(inputFeature.id, featuresUpdatedInFrame.features[0]!!.id)
    }

    @Test
    fun shouldReadFeatureByAuthorTs(){
        // Given:
        val inputFeature = ProxyFeatureGenerator.generateRandomFeature()

        // And:
        val insertedFeatureMeta = insertFeatureAndGetMeta(inputFeature)

        // And:
        val featuresByAuthorTs = executeMetaQuery(MetaQuery(
            TupleColumn.authorTs(),
            DoubleOp.EQ,
            insertedFeatureMeta.authorTs
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
            collectionIds += collection!!.id
            query.metadata = MetaOr(
                MetaQuery(TupleColumn.author(), StringOp.EQUALS, "this_is_totally_off"),
                MetaQuery(TupleColumn.appId(), StringOp.STARTS_WITH, appId.substring(0, 2))
            )
        }).features

        // Then:
        assertEquals(10, featuresByAppIdAndAuthor.size)
        assertTrue(featuresByAppIdAndAuthor.map { it!!.id }
            .containsAll(featuresToCreate.map { it.id }))
    }

    private fun insertFeatureAndGetMeta(feature: NakshaFeature): Metadata {
        insertFeature(feature = feature)
        return executeRead(ReadFeatures().apply {
            collectionIds += collection!!.id
            featureIds += feature.id
        }).tuples[0]!!.tuple!!.meta!!
    }

    private fun executeMetaQuery(metaQuery: IMetaQuery): SuccessResponse {
        return executeRead(ReadFeatures().apply {
            collectionIds += collection!!.id
            query.metadata = metaQuery
        })
    }
}