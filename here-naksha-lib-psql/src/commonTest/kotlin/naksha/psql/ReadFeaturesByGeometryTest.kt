package naksha.psql

import naksha.geo.*
import naksha.model.objects.NakshaFeature
import naksha.model.request.ReadFeatures
import naksha.model.request.SuccessResponse
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.model.request.query.*
import naksha.psql.assertions.AnyObjectFluidAssertions.Companion.assertThatAnyObject
import naksha.model.RandomFeatures.RandomFeatures_C.randomFeature
import kotlin.test.*

class ReadFeaturesByGeometryTest : PgTestBase(collection = null, catalogId = "") {

    @Test
    fun shouldReturnSavedGeometry() {
        testWithCollection("shouldReturnSavedGeometry")

        // Given: feature with geometry
        val feature = NakshaFeature().apply {
            id = "test_feature"
            geometry = SpPoint(PointCoord(1.0, 2.0, 0.0))
        }

        // When: executing feature write request
        executeWrite(
            WriteRequest().add(
                Write().createFeature(collection, feature)
            )
        )

        // And: reading feature
        val retrievedFeatures = executeRead(
            ReadFeatures().apply {
                catalogId = collection.catalogId
                collectionId = collection.id
                featureIds += feature.id
            }
        ).features

        // Then: geometry is there and it is what we inserted
        assertEquals(1, retrievedFeatures.size)
        assertThatAnyObject(retrievedFeatures[0]!!.geometry!!)
            .isIdenticalTo(feature.geometry!!)
    }

    @Test
    fun shouldReturnGeometryWithoutElevation() {
        testWithCollection("shouldReturnGeometryWithoutElevation")

        // Given: feature with geometry
        val featureGeometry = SpPoint(PointCoord(1.0, 2.0))
        val feature = NakshaFeature().apply {
            id = "test_feature"
            geometry = featureGeometry
        }

        // When: executing feature write request
        executeWrite(
            WriteRequest().add(
                Write().createFeature(collection, feature)
            )
        )

        // And: reading feature
        val retrievedFeatures = executeRead(
            ReadFeatures().apply {
                catalogId = collection.catalogId
                collectionId = collection.id
                featureIds += feature.id
            }
        ).features

        // Then: geometry is there and it is what we inserted
        assertEquals(1, retrievedFeatures.size)
        val retrievedFeature = assertNotNull(retrievedFeatures[0])
        val geometry = assertNotNull(retrievedFeature.geometry)
        assertThatAnyObject(geometry).isIdenticalTo(featureGeometry)
    }

    @Test
    fun shouldReadFeatureByBbox() {
        testWithCollection("shouldReadFeatureByBbox")

        // Given: features to create
        val feature = randomFeature()

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
        testWithCollection("shouldReturnFeaturesByHereTile")

        // Given
        val featureInPrague = randomFeature().apply {
            referencePoint = SpPoint(
                PointCoord(
                    longitude = 14.4178737288,
                    latitude = 50.0872507931
                )
            )
        }
        val featureInParis = randomFeature().apply {
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
        testWithCollection("shouldReturnFeaturesWithCombinedQuery")

        // Given
        val somePlaceInPrague = randomFeature().apply {
            referencePoint = SpPoint(
                PointCoord(
                    longitude = 14.4178737288,
                    latitude = 50.0872507931
                )
            )
        }
        val valencia = randomFeature().apply {
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

    @Test
    fun shouldReadByCorridor(){
        testWithCollection("shouldReadByCorridor")

        // Given
        val feature = randomFeature().apply {
            geometry = SpLineString().withCoordinates(LineStringCoord(
                PointCoord(longitude = 45.0, latitude = 45.0),
                PointCoord(longitude = 45.0, latitude = 46.0),
            ))
        }

        // And
        val point = SpPoint(PointCoord(45.000001, 45.0))
        val buffer = SpBuffer(distance = 100000.0)
        val readByCorridor = SpIntersects(point, buffer)

        // When
        insertFeature(feature)

        // And
        val features = executeSpatialQuery(readByCorridor).features

        // Then:
        assertEquals(1, features.size)
        assertEquals(feature.id, features[0]!!.id)
    }

    @Test
    fun shouldDistinguishDifferentBufferModes(){
        testWithCollection("shouldDistinguishDifferentBufferModes")

        /**
         * Note: samples & values based on https://postgis.net/workshops/postgis-intro/geography.html
         * Actual distance between LAX and NRT:
         * - 8833954.76996256 meters (Cartesian) - used by 'geography'
         * - 258.146005837336 degrees (SRS) - used by 'geometry'
         */

        // Given
        val laxAirportCoord = PointCoord(longitude = -118.4079, latitude = 33.9434)
        val nrtAirportCoord = PointCoord(longitude = 139.733, latitude = 35.567)
        val laxAirport = randomFeature().apply {
            geometry = SpPoint(laxAirportCoord)
        }

        // When:
        insertFeature(laxAirport)

        // And:
        val featuresWithinThreeHundredMetersFromNrt = executeSpatialQuery(SpIntersects(
            SpPoint(nrtAirportCoord),
            SpBuffer(distance = 300.0, geography = true)
        )).features

        // Then:
        assertTrue(featuresWithinThreeHundredMetersFromNrt.isEmpty())

        // When:
        val featuresWithinThreeHundredDegreesFromNrt = executeSpatialQuery(SpIntersects(
            SpPoint(nrtAirportCoord),
            SpBuffer(distance = 300.0, geography = false)
        )).features

        // Then:
        assertEquals(1, featuresWithinThreeHundredDegreesFromNrt.size)
        assertEquals(laxAirport.id, featuresWithinThreeHundredDegreesFromNrt[0]!!.id)
    }

    private fun executeSpatialQuery(spatialQuery: ISpatialQuery): SuccessResponse {
        return executeRead(ReadFeatures().apply {
            catalogId = collection.catalogId
            collectionId = collection.id
            query.spatial = spatialQuery
        })
    }
}