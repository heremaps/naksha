package naksha.psql

import naksha.geo.HereTile
import naksha.geo.PointCoord
import naksha.geo.SpPoint
import naksha.model.request.ReadFeatures
import naksha.model.RandomFeatures.RandomFeatures_C.randomFeature
import kotlin.test.*

class ReadFeaturesByRefTilesTest : PgTestBase(collection = null, mapId = "") {

    private val pragueCityHall = randomFeature().apply {
        referencePoint = SpPoint(PointCoord(
            longitude = 14.4178737288,
            latitude = 50.0872507931
        ))
    }
    private val eiffelTower = randomFeature().apply {
        referencePoint = SpPoint(PointCoord(
            longitude = 2.294513484201658,
            latitude = 48.858546539609414
        ))
    }
    private val zagrebPromenade = randomFeature().apply {
        referencePoint = SpPoint(PointCoord(
            15.972726122592436,
            45.81509550000001
        ))
    }
    private val zagrebTileLv12 = HereTile("122010112103")
    private val pragueTileLv12 = HereTile("122010322102")
    private val bolognaTileLv12 = HereTile("120232222021") // empty!

    fun populateFeatures() {
        insertFeatures(
            pragueCityHall,
            eiffelTower,
            zagrebPromenade
        )
    }

    @Test
    fun readFeaturesByRefTiles() {
        testWithCollection("readFeaturesByRefTiles")
        populateFeatures()

        // Given:
        val getFeaturesFromZagrebAndPrague = ReadFeatures().apply {
            mapId = collection.mapId
            collectionIds += collection!!.id
            query.refTiles += listOf(zagrebTileLv12.intKey, pragueTileLv12.intKey)
        }

        // When:
        val features = executeRead(getFeaturesFromZagrebAndPrague).features

        // Then:
        assertEquals(2, features.size)
        val featureIds = features.map { it!!.id }
        assertTrue(featureIds.containsAll(listOf(pragueCityHall.id, zagrebPromenade.id)))
    }

    @Test
    fun returnNothingOnEmptyTiles() {
        testWithCollection("returnNothingOnEmptyTiles")
        populateFeatures()

        // Given:
        val getFeaturesFromBologna = ReadFeatures().apply {
            mapId = collection.mapId
            collectionIds += collection.id
            query.refTiles += bolognaTileLv12.intKey
        }

        // When:
        val features = executeRead(getFeaturesFromBologna).features

        // Then:
        assertTrue(features.isEmpty())
    }
}