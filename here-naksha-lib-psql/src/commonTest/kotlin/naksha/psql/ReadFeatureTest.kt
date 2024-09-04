package naksha.psql

import naksha.geo.PointCoord
import naksha.geo.SpPoint
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.request.*
import naksha.psql.assertions.AnyObjectFluidAssertions.Companion.assertThatAnyObject
import naksha.psql.base.PgTestBase
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
}