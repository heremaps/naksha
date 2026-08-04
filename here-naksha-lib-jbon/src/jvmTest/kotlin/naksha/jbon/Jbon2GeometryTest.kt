package naksha.jbon

import naksha.base.PAnyMap
import naksha.base.Binary
import naksha.base.PTypedMap
import naksha.base.Base
import naksha.geo.GeoUtil
import naksha.geo.SpBoundingBox
import naksha.geo.SpGeometry
import naksha.geo.SpPoint
import naksha.geo.SpPolygon
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * JVM-only tests for geometry (TWKB) encoding and decoding in [JbEncoder2] / [JbDecoder2].
 *
 * These tests require the JVM `GeoUtil` implementation backed by JTS; they are therefore placed in
 * `jvmTest` rather than `commonTest`.
 */
class Jbon2GeometryTest {

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Extract a [ByteArray] from an encoder's current buffer (offset 0 .. end). */
    private fun encoderBytes(enc: JbEncoder2): ByteArray = ByteArray(enc.end) { enc.getInt8(it) }

    /** Decode the first value at offset 0 from the given bytes (no file header). */
    private fun decodeFirst(bytes: ByteArray): Any? {
        val bin = Binary()
        bin.view = Base.newDataView(bytes)
        bin.end = bytes.size
        val dec = JbDecoder2()
        dec.bytes = bin
        dec.end = bytes.size
        return dec.decodeValueAt(0)
    }

    // -----------------------------------------------------------------------
    // encodeTwkb — raw bytes
    // -----------------------------------------------------------------------

    @Test
    fun testEncodeTwkbRawBytes() {
        val point = SpPoint(0.0, 0.0)
        val twkbBytes = GeoUtil.toTWKB(point)!!
        assertTrue(twkbBytes.isNotEmpty(), "GeoUtil.toTWKB must return non-empty bytes")

        val enc = JbEncoder2()
        val start = enc.encodeTwkb(twkbBytes)

        val leadIn = enc.getInt8(start).toInt() and 0xff
        assertEquals(JB2_CLASS_STRUCT, leadIn and JB2_CLASS_MASK,   "lead-in class must be JB2_CLASS_STRUCT")
        assertEquals(JB2_STRUCT_TWKB,  leadIn and JB2_STRUCT_TYPE_MASK, "lead-in type must be JB2_STRUCT_TWKB (9)")
        assertTrue((leadIn and JB2_STRUCT_SIZE_MASK) != 0, "ss must not be 00 for non-empty TWKB (spec forbids it)")
    }

    @Test
    fun testEncodeTwkbEmptyThrows() {
        val enc = JbEncoder2()
        assertFailsWith<IllegalArgumentException> { enc.encodeTwkb(ByteArray(0)) }
    }

    // -----------------------------------------------------------------------
    // encodeGeometry — SpPoint / SpPolygon
    // -----------------------------------------------------------------------

    @Test
    fun testEncodeGeometryPoint() {
        val point = SpPoint(13.4050, 52.5200)  // Berlin
        val enc = JbEncoder2()
        enc.encodeGeometry(point)

        val leadIn = enc.getInt8(0).toInt() and 0xff
        assertEquals(JB2_CLASS_STRUCT, leadIn and JB2_CLASS_MASK)
        assertEquals(JB2_STRUCT_TWKB,  leadIn and JB2_STRUCT_TYPE_MASK)
        assertTrue(enc.end > 2, "encoded geometry must be more than just lead-in + size byte")
    }

    @Test
    fun testEncodeGeometryPolygon() {
        val poly = SpPolygon(SpBoundingBox(0.0, 0.0, 1.0, 1.0))
        val enc = JbEncoder2()
        enc.encodeGeometry(poly)

        val leadIn = enc.getInt8(0).toInt() and 0xff
        assertEquals(JB2_STRUCT_TWKB, leadIn and JB2_STRUCT_TYPE_MASK)
    }

    // -----------------------------------------------------------------------
    // encodeValue dispatches SpGeometry
    // -----------------------------------------------------------------------

    @Test
    fun testEncodeValueDispatchesGeometry() {
        val point = SpPoint(7.0, 51.0)
        val enc = JbEncoder2()
        enc.encodeValue(point)

        val leadIn = enc.getInt8(0).toInt() and 0xff
        assertEquals(JB2_STRUCT_TWKB, leadIn and JB2_STRUCT_TYPE_MASK,
            "encodeValue(SpGeometry) must emit a TWKB structure")
    }

    // -----------------------------------------------------------------------
    // Round-trip: encode → decode
    // -----------------------------------------------------------------------

    @Test
    fun testRoundTripPoint() {
        val lon = 13.4050
        val lat = 52.5200
        val point = SpPoint(lon, lat)

        val enc = JbEncoder2()
        enc.encodeGeometry(point)
        val bytes = encoderBytes(enc)

        val decoded = decodeFirst(bytes)
        assertNotNull(decoded, "decoded value must not be null")
        assertTrue(decoded is SpGeometry, "decoded value must be an SpGeometry, was: ${decoded!!::class}")

        val decodedPoint = decoded as SpPoint
        val coords = decodedPoint.getCoordinates()
        // TWKB uses fixed-point with precision 7; tolerance 1e-5 is safe
        assertEquals(lon, coords.getLongitude(), 1e-5)
        assertEquals(lat, coords.getLatitude(),  1e-5)
    }

    // -----------------------------------------------------------------------
    // buildTupleFromMap: geometry is now included (not skipped)
    // -----------------------------------------------------------------------

    @Test
    fun testBuildTupleIncludesGeometry() {
        val feature = PAnyMap()
        feature["id"] = "test-feature"
        feature["geometry"] = SpPoint(13.0, 52.0)

        val enc = JbEncoder2()
        @Suppress("UNCHECKED_CAST")
        val tupleBytes = enc.buildTupleFromMap(feature as PTypedMap<String, *>)

        val dec = JbDecoder2()
        dec.mapBytes(tupleBytes)
        val decoded = dec.toAnyObject()

        val decodedGeometry = decoded["geometry"]
        assertNotNull(decodedGeometry, "geometry key must be present in decoded feature (not skipped)")
        assertTrue(decodedGeometry is SpGeometry,
            "geometry value must decode to SpGeometry, was: ${decodedGeometry!!::class}")
    }
}
