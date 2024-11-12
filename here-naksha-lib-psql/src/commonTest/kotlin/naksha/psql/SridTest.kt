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
import naksha.psql.util.ProxyFeatureGenerator.generateRandomFeature
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SridTest : PgTestBase() {

    data class SridTestConfig(val encodingName: String, val flags: Flags, val collectionId: String)

    private companion object {
        // test data
        const val EXPECTED_SRID = 4326

        // initialization helpers
        const val NOT_INITIALIZED = 0
        const val INITIALIZED = 1
        val initializedCollections = AtomicInt(NOT_INITIALIZED)

        // test case config
        fun flagsFor(encoding: Int): Flags = Flags().geoEncoding(encoding)
        val testConfigs: Map<Int, SridTestConfig> = mapOf(
            TWKB to SridTestConfig(
                encodingName = "TWKB",
                flags = flagsFor(TWKB),
                collectionId = "srid_test_twkb"
            ),
            TWKB_GZIP to SridTestConfig(
                encodingName = "TWKB_GZIP",
                flags = flagsFor(TWKB_GZIP),
                collectionId = "srid_test_twkb_gzip"
            ),
            WKB to SridTestConfig(
                encodingName = "WKB",
                flags = flagsFor(WKB),
                collectionId = "srid_test_wkb"
            ),
            WKB_GZIP to SridTestConfig(
                encodingName = "WKB_GZIP",
                flags = flagsFor(WKB_GZIP),
                collectionId = "srid_test_wkb_gzip"
            ),
            EWKB to SridTestConfig(
                encodingName = "EWKB",
                flags = flagsFor(EWKB),
                collectionId = "srid_test_ewkb"
            ),
            EWKB_GZIP to SridTestConfig(
                encodingName = "EWKB_GZIP",
                flags = flagsFor(EWKB_GZIP),
                collectionId = "srid_test_ewkb_gzip"
            ),
            GEO_JSON to SridTestConfig(
                encodingName = "GEO_JSON",
                flags = flagsFor(GEO_JSON),
                collectionId = "srid_test_geojson"
            ),
            GEO_JSON_GZIP to SridTestConfig(
                encodingName = "GEO_JSON_GZIP",
                flags = flagsFor(GEO_JSON_GZIP),
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
        val feature = generateRandomFeature().apply {
            geometry = SpLineString().withCoordinates(
                LineStringCoord(
                    PointCoord(longitude = 25.0, latitude = 25.0),
                    PointCoord(longitude = 25.0, latitude = 26.0),
                )
            )
        }
        val writeFeatureReq = WriteRequest().add(
            Write().createFeature(
                map = null,
                collectionId = testConfig.collectionId,
                feature = feature
            )
        )
        executeWrite(writeFeatureReq)

        // When: selected it for SRID
        val srid = selectSrid(
            collectionName = testConfig.collectionId,
            flags = testConfig.flags,
            featureId = feature.id
        )

        // Then
        assertEquals(EXPECTED_SRID, srid, "Invalid SRID for encoding: ${testConfig.encodingName}")
    }

    private fun selectSrid(collectionName: String, flags: Int, featureId: String): Int {
        return try {
            val sql = """
                SELECT ST_SRID(naksha_geometry(${PgColumn.geo}, $flags)) as srid
                FROM $collectionName
                WHERE ${PgColumn.id} = '$featureId'
            """.trimIndent()
            val cursor = useConnection().execute(sql)
            val res = cursor.use {
                cursor.next()
                cursor.column("srid")
            }
            res as Int
        } catch (e: Exception) {
            throw Exception("Failed selecting srid for flags: $flags", e)
        }
    }

    private fun writeSingleCollectionOp(collectionId: String, flags: Flags): Write {
        val collection = NakshaCollection(collectionId)
        collection.defaultFlags = flags
        return Write().createCollection(
            map = null,
            collection = collection
        )
    }
}