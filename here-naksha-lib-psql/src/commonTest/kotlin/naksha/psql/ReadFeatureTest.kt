package naksha.psql

import naksha.geo.PointCoord
import naksha.geo.SpBoundingBox
import naksha.geo.SpPoint
import naksha.model.SessionOptions
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.request.ReadFeatures
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.model.request.query.*
import naksha.psql.assertions.AnyObjectFluidAssertions.Companion.assertThatAnyObject
import naksha.psql.base.PgTestBase
import naksha.psql.util.ProxyFeatureGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Class for testing "not usual" reading scenarios
 * Basic reading (by id) is covered in tests with writes anyway.
 */
class ReadFeatureTest : PgTestBase(NakshaCollection("read_feature_test_c")) {

    @Test
    fun shouldReturnSavedGeometry() {
        // Given: feature with geometry
        val feature = NakshaFeature().apply {
            id = "test_feature"
            geometry = SpPoint(PointCoord(1.0, 2.0, 0.0))
        }

        // When: executing feature write request
        executeWrite(
            WriteRequest().add(
                Write().createFeature(null, collection!!.id, feature)
            )
        )

        // And: reading feature
        val retrievedFeature = executeRead(
            ReadFeatures().apply {
                collectionIds += collection.id
                featureIds += feature.id
            }
        ).let { response ->
            val features = response.features
            assertEquals(1, features.size)
            features[0]!!
        }

        // Then: geometry is there and it is what we inserted
        assertNotNull(retrievedFeature.geometry)
        assertThatAnyObject(retrievedFeature.geometry!!)
            .isIdenticalTo(feature.geometry!!)
    }

    @Test
    fun shouldReadFeatureByBbox() {
        // Given: features to create
        val featureToCreate = ProxyFeatureGenerator.generateRandomFeature()
        val writeFeaturesReq = WriteRequest().apply {
            add(Write().createFeature(null, collection!!.id, featureToCreate))
        }

        // When: executing feature write request
        executeWrite(writeFeaturesReq)

        // And: execute read by bounding box.
        val featuresByBBox = executeRead(ReadFeatures().apply {
            collectionIds += collection!!.id
            query.spatial =
                SpIntersects(
                    SpBoundingBox(featureToCreate.geometry).addMargin(0.0000001).toPolygon()
                )
        })

        // Then:
        assertEquals(1, featuresByBBox.features.size)
        assertEquals(featureToCreate.id, featuresByBBox.features[0]!!.id)
    }

    @Test
    fun shouldReadFeatureByMetadata() {
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
                MetaQuery(TupleColumn.appId(), StringOp.STARTS_WITH, appId.substring(0,2))
            )
        })

        // Then:
        assertEquals(1, featuresByAppIdAndAuthor.features.size)
        assertEquals(featureToCreate.id, featuresByAppIdAndAuthor.features[0]!!.id)
    }
}