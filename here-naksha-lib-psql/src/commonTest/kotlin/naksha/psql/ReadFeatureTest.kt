package naksha.psql

import naksha.geo.PointCoord
import naksha.geo.SpBoundingBox
import naksha.geo.SpPoint
import naksha.geo.SpPolygon
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.request.ReadFeatures
import naksha.model.request.RequestQuery
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.model.request.query.SpIntersects
import naksha.psql.assertions.AnyObjectFluidAssertions.Companion.assertThatAnyObject
import naksha.psql.assertions.NakshaFeatureFluidAssertions.Companion.assertThatFeature
import naksha.psql.base.PgTestBase
import naksha.psql.util.ProxyFeatureGenerator.generateRandomFeature
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
    fun shouldReturnFeaturesByGeoIntersection() {
        // Given: some coordinates
        val czechRepublic = SpBoundingBox(
            west = 12.204373,
            south = 48.679486,
            east = 18.779690,
            north = 50.992447
        )
        val berlinCenter = SpPoint(PointCoord(13.387603, 52.516655))
        val oldTownInPrague = SpPoint(PointCoord(14.417014, 50.084276))

        // And: features in different places
        val featureInPrague = generateRandomFeature().apply {
            geometry = oldTownInPrague
        }
        val featureInBerlin = generateRandomFeature().apply {
            geometry = berlinCenter
        }

        // And: these features are stored
        executeWrite(
            WriteRequest()
                .add(Write().createFeature(null, collection!!.id, featureInBerlin))
                .add(Write().createFeature(null, collection.id, featureInPrague))
        )

        // When: asking for all features in Czech Republic
        val getAllFeaturesFromCzech = ReadFeatures().apply {
            collectionIds += collection.id
            query = RequestQuery().apply {
                spatial = SpIntersects(SpPolygon(czechRepublic))
                //hex: e3081d0105a4f5b174d8999fd00300a4c1da3e00000094b8871600a3c1da3e00000093b8871600
            }
        }
        val retrievedFeatures = executeRead(getAllFeaturesFromCzech).features

        // Then:
        assertEquals(1, retrievedFeatures.size)
        assertThatFeature(retrievedFeatures[0]!!).isIdenticalTo(
            other = featureInPrague,
            ignoreProps = true
        )
    }
}