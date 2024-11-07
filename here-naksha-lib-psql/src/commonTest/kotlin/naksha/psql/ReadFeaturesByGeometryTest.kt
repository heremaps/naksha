package naksha.psql

import naksha.geo.*
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.request.ReadFeatures
import naksha.model.request.SuccessResponse
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.model.request.query.ISpatialQuery
import naksha.model.request.query.SpIntersects
import naksha.model.request.query.SpOr
import naksha.model.request.query.SpRefInHereTile
import naksha.psql.assertions.AnyObjectFluidAssertions.Companion.assertThatAnyObject
import naksha.psql.base.PgTestBase
import naksha.psql.util.ProxyFeatureGenerator.generateRandomFeature
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReadFeaturesByGeometryTest : PgTestBase(NakshaCollection("read_by_geometry_test")) {

    @AfterTest
    fun cleanUp() {
        dropCollection()
    }

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
        val retrievedFeatures = executeRead(
            ReadFeatures().apply {
                collectionIds += collection.id
                featureIds += feature.id
            }
        ).features

        // Then: geometry is there and it is what we inserted
        assertEquals(1, retrievedFeatures.size)
        assertThatAnyObject(retrievedFeatures[0]!!.geometry!!)
            .isIdenticalTo(feature.geometry!!)
    }

    @Test
    fun shouldReadFeatureByBbox() {
        // Given: features to create
        val feature = generateRandomFeature()

        // When: executing feature write request
        insertFeature(feature)

        // And: execute read by bounding box.
        val featuresByBBox = executeSpatialQuery(
            SpIntersects(
                SpBoundingBox(feature.geometry).addMargin(0.0000001).toPolygon()
            )
        )

        // Then:
        assertEquals(1, featuresByBBox.features.size)
        assertEquals(feature.id, featuresByBBox.features[0]!!.id)
    }

    @Test
    fun shouldReturnFeaturesByHereTile() {
        // Given
        val featureInPrague = generateRandomFeature().apply {
            referencePoint = SpPoint(
                PointCoord(
                    longitude = 14.4178737288,
                    latitude = 50.0872507931
                )
            )
        }
        val featureInParis = generateRandomFeature().apply {
            referencePoint = SpPoint(
                PointCoord(
                    longitude = 2.294513484201658,
                    latitude = 48.858546539609414
                )
            )
        }

        // When
        insertFeatures(featureInPrague, featureInParis)

        // And
        val czechiaLvl7 = HereTile("1220103")
        val featuresInCzechia = executeSpatialQuery(SpRefInHereTile(czechiaLvl7)).features

        // Then:
        assertEquals(1, featuresInCzechia.size)
        assertEquals(featureInPrague.id, featuresInCzechia[0]!!.id)
    }

    @Test
    fun shouldReturnFeaturesWithCombinedQuery() {
        // Given
        val somePlaceInPrague = generateRandomFeature().apply {
            referencePoint = SpPoint(
                PointCoord(
                    longitude = 14.4178737288,
                    latitude = 50.0872507931
                )
            )
        }
        val valencia = generateRandomFeature().apply {
            geometry = SpPolygon(
                SpBoundingBox(
                    west = -0.412674,
                    south = 39.441761,
                    east = -0.334053,
                    north = 39.499802
                )
            )
        }

        // And
        val hasGeometryInSpain = SpIntersects(
            SpPolygon(
                SpBoundingBox(-10.239258, 35.639441, 2.787695, 43.921637)
            )
        )
        val hasRefPointInPrague = SpRefInHereTile(HereTile("122010322102"))
        val query = SpOr(hasGeometryInSpain, hasRefPointInPrague)

        // When:
        insertFeatures(valencia, somePlaceInPrague)

        // And:
        val features = executeSpatialQuery(query).features

        // Then
        assertEquals(2, features.size)
        val featureIds = features.map { it!!.id }
        assertTrue(featureIds.containsAll(listOf(somePlaceInPrague.id, valencia.id)))
    }

    private fun executeSpatialQuery(spatialQuery: ISpatialQuery): SuccessResponse {
        return executeRead(ReadFeatures().apply {
            collectionIds += collection!!.id
            query.spatial = spatialQuery
        })
    }
}