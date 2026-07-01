package naksha.psql

import naksha.geo.LineStringCoord
import naksha.geo.PointCoord
import naksha.geo.SpLineString
import naksha.model.objects.NakshaCollection
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.model.RandomFeatures.RandomFeatures_C.randomFeature
import naksha.psql.PgTest.PgTest_C.TEST_MAP_ID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies that `naksha_geometry` round-trips with `SRID = 4326`.
 *
 * Naksha now stores geometries exclusively as raw `TWKB`, so there is just a single encoding to cover.
 */
class SridTest : PgTestBase() {

    private companion object {
        const val EXPECTED_SRID = 4326
        const val COLLECTION_ID = "srid_test_twkb"
    }

    @BeforeTest
    fun setupCollection() {
        val req = WriteRequest().add(
            Write().createCollection(NakshaCollection(COLLECTION_ID, TEST_MAP_ID))
        )
        executeWrite(req)
    }

    @Test
    fun shouldPreserveSrid() {
        val feature = randomFeature().apply {
            geometry = SpLineString().withCoordinates(
                LineStringCoord(
                    PointCoord(longitude = 25.0, latitude = 25.0),
                    PointCoord(longitude = 25.0, latitude = 26.0),
                )
            )
        }
        executeWrite(
            WriteRequest().add(
                Write().createFeature(
                    mapId = map.id,
                    collectionId = COLLECTION_ID,
                    feature = feature
                )
            )
        )

        val srid = selectSrid(map.id, COLLECTION_ID, feature.id)
        assertEquals(EXPECTED_SRID, srid)
    }

    private fun selectSrid(mapId: String, collectionName: String, featureId: String): Int {
        val sql = """
            SELECT ST_SRID(naksha_geometry(${COL_GEOMETRY})) as srid
            FROM $mapId.$collectionName
            WHERE ${COL_ID} = '$featureId'
        """.trimIndent()
        return storage.adminConnection().use { conn ->
            conn.execute(sql).fetch().use { cursor ->
                cursor.column("srid") as Int
            }
        }
    }
}
