package naksha.psql

import naksha.base.AtomicInt
import naksha.geo.LineStringCoord
import naksha.geo.PointCoord
import naksha.geo.SpLineString
import naksha.model.*
import naksha.model.GeoEncoding.GeoEncoding_C.EWKB
import naksha.model.GeoEncoding.GeoEncoding_C.EWKB_GZIP
import naksha.model.GeoEncoding.GeoEncoding_C.GEO_JSON
import naksha.model.GeoEncoding.GeoEncoding_C.GEO_JSON_GZIP
import naksha.model.GeoEncoding.GeoEncoding_C.TWKB
import naksha.model.GeoEncoding.GeoEncoding_C.TWKB_GZIP
import naksha.model.GeoEncoding.GeoEncoding_C.WKB
import naksha.model.GeoEncoding.GeoEncoding_C.WKB_GZIP
import naksha.model.objects.NakshaCollection
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.psql.base.PgTestBase
import naksha.model.RandomFeatures.RandomFeatures_C.randomFeature
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SridTest : PgTestBase() {


    /**
    This might look odd at first glance but there's a reason to have test configs as separate being.
    Currently in Naksha, when we want to write features with specific encoding we need to specify this as a collection property (via NakshaCollection.defaultFlags). Then, whenever we write features to collection, this field is read and proper encoding is enforced on geometry, tags etc.

    In the tests below, we are validating correct encoding of geometry (more specifically, we check whether naksha_geometry function, when run against persisted feature, returns geometry with SRID set to 4326 which is our default and only supported SRID).
    To test all encodings we have, we need separate collection for each encoding - we need features to be properly encoded before persisted so we need proper collection.flags to be set (see 1st paragraph above).

    Why not use single collection and modify it with defferent flags for each test?
    - Because collections and their properties are cached, so if we had shared collection for all test cases, the application wouldn't fetch collection from DB, rather it will use what it has in cache (with previous encoding) and we would end up with encoding not matching our expectation
    - To avoid the above, the collection cache would have to be cleared, and since it is not public (rightly so), the only way to do that is dropping the collection - this already makes the whole process more complicated and slowe than simply having N collections for N encodings
     */
    private data class SridTestConfig(val encodingName: String, val flags: Flags, val mapId: String, val collectionId: String)

    private companion object {
        // test data
        const val EXPECTED_SRID = 4326

        // initialization helpers
        const val NOT_INITIALIZED = 0
        const val INITIALIZED = 1
        val initializedCollections = AtomicInt(NOT_INITIALIZED)

        // test case config
        fun flagsFor(encoding: Int): Flags = Flags().withGeoEncoding(encoding)
    }

    private val testConfigs: Map<Int, SridTestConfig> by lazy {
        mapOf(
            TWKB to SridTestConfig(
                encodingName = "TWKB",
                flags = flagsFor(TWKB),
                mapId = env.mapId,
                collectionId = "srid_test_twkb"
            ),
            TWKB_GZIP to SridTestConfig(
                encodingName = "TWKB_GZIP",
                flags = flagsFor(TWKB_GZIP),
                mapId = env.mapId,
                collectionId = "srid_test_twkb_gzip"
            ),
            WKB to SridTestConfig(
                encodingName = "WKB",
                flags = flagsFor(WKB),
                mapId = env.mapId,
                collectionId = "srid_test_wkb"
            ),
            WKB_GZIP to SridTestConfig(
                encodingName = "WKB_GZIP",
                flags = flagsFor(WKB_GZIP),
                mapId = env.mapId,
                collectionId = "srid_test_wkb_gzip"
            ),
            EWKB to SridTestConfig(
                encodingName = "EWKB",
                flags = flagsFor(EWKB),
                mapId = env.mapId,
                collectionId = "srid_test_ewkb"
            ),
            EWKB_GZIP to SridTestConfig(
                encodingName = "EWKB_GZIP",
                flags = flagsFor(EWKB_GZIP),
                mapId = env.mapId,
                collectionId = "srid_test_ewkb_gzip"
            ),
            GEO_JSON to SridTestConfig(
                encodingName = "GEO_JSON",
                flags = flagsFor(GEO_JSON),
                mapId = env.mapId,
                collectionId = "srid_test_geojson"
            ),
            GEO_JSON_GZIP to SridTestConfig(
                encodingName = "GEO_JSON_GZIP",
                flags = flagsFor(GEO_JSON_GZIP),
                mapId = env.mapId,
                collectionId = "srid_test_geojson_gzip"
            )
        )
    }


    @BeforeTest
    fun setupCollections() {
        if (initializedCollections.compareAndSet(NOT_INITIALIZED, INITIALIZED)) {
            val writeCollectionsReq = WriteRequest()
            testConfigs.forEach { (_, testConfig) ->
                writeCollectionsReq.add(writeSingleCollectionOp(
                    collectionId = testConfig.collectionId,
                    flags = testConfig.flags
                ))
            }
            executeWrite(writeCollectionsReq)
        }
    }

    @Test
    fun shouldPreserveSridForTwkb() {
        runTestFor(TWKB)
    }

    @Test
    fun shouldPreserveSridForTwkbGzip() {
        runTestFor(TWKB_GZIP)
    }

    @Test
    fun shouldPreserveSridForWkb() {
        runTestFor(WKB)
    }

    @Test
    fun shouldPreserveSridForWkbGzip() {
        runTestFor(WKB_GZIP)
    }

    @Test
    fun shouldPreserveSridForEwkb() {
        runTestFor(EWKB)
    }

    @Test
    fun shouldPreserveSridForEwkbGzip() {
        runTestFor(EWKB_GZIP)
    }

    @Test
    fun shouldPreserveSridForGeoJson() {
        runTestFor(GEO_JSON)
    }

    @Test
    fun shouldPreserveSridForGeoJsonGzip() {
        runTestFor(GEO_JSON_GZIP)
    }

    private fun runTestFor(encoding: Int) {
        // Given: test config
        val testConfig = testConfigs[encoding]
            ?: throw IllegalArgumentException("No config for encoding: $encoding")

        // And: inserted feature with geometry
        val feature = randomFeature().apply {
            geometry = SpLineString().withCoordinates(
                LineStringCoord(
                    PointCoord(longitude = 25.0, latitude = 25.0),
                    PointCoord(longitude = 25.0, latitude = 26.0),
                )
            )
        }
        val writeFeatureReq = WriteRequest().add(
            Write().createFeature(
                mapId = testConfig.mapId,
                collectionId = testConfig.collectionId,
                feature = feature
            )
        )
        executeWrite(writeFeatureReq)

        // When: selected it for SRID
        val srid = selectSrid(
            mapId = testConfig.mapId,
            collectionName = testConfig.collectionId,
            flags = testConfig.flags,
            featureId = feature.id
        )

        // Then
        assertEquals(EXPECTED_SRID, srid, "Invalid SRID for encoding: ${testConfig.encodingName}")
    }

    private fun selectSrid(mapId: String, collectionName: String, flags: Int, featureId: String): Int {
        return try {
            val sql = """
                SELECT ST_SRID(naksha_geometry(${PgColumn.geo}, $flags)) as srid
                FROM $mapId.$collectionName
                WHERE ${PgColumn.id} = '$featureId'
            """.trimIndent()
            storage.adminConnection().use { conn ->
                conn.execute(sql).fetch().use { cursor ->
                    cursor.column("srid") as Int
                }
            }
        } catch (e: Exception) {
            throw Exception("Failed selecting srid for flags: $flags", e)
        }
    }

    private fun writeSingleCollectionOp(collectionId: String, flags: Flags): Write {
        val collection = NakshaCollection(collectionId)
        collection.defaultFlags = flags
        return Write().createCollection(collection)
    }
}