package naksha.jbon

import naksha.base.AnyObject
import naksha.base.Binary
import naksha.base.Int64
import naksha.base.MapProxy
import naksha.base.Platform
import naksha.geo.GeoUtil
import naksha.geo.SpGeometry
import naksha.geo.SpPoint
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * JVM-only tests for the [JbDecoder2] members-book ([JB2_REF_BOOK_MEMBERS]) resolution path.
 *
 * These tests verify that:
 * 1. A primitive value (e.g. an integer) stored in the members book is correctly re-inserted
 *    at the path where the encoder placed a members-reference.
 * 2. A geometry stored in the members book as raw TWKB [ByteArray] (as it would arrive from a
 *    PostgreSQL `bytea` column) is converted to [SpGeometry] by [GeoUtil.fromTWKB] and returned
 *    as the decoded value.
 */
class Jbon2MembersTest {

    // -----------------------------------------------------------------------
  // Minimal IBook implementation backed by a plain list — test helper only

     /** A minimal read-only [IBook] backed by a [List] of arbitrary values. */
    private class ListDict(private val entries: List<Any?>) : IBook {
        override val id: String? = null
        override val bookType: BookType = BookType.MEMBER_BOOK
        override val databaseNumber: Int64? = null
        override val featureNumber: Int64? = null
        override val length: Int get() = entries.size
        override fun get(index: Int): Any? = entries.getOrNull(index)
        override fun indexOfString(string: String): Int = entries.indexOfFirst { it == string }
        override fun getStringAt(index: Int): String? = entries.getOrNull(index)?.toString()
        override fun getAllWithHash(hash: Int): List<DictEntry> = emptyList()
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Decode the first unit at offset 0 from raw bytes (no file header), using [membersDict]. */
    private fun decodeFirst(bytes: ByteArray, membersDict: IBook? = null): Any? {
        val bin = Binary()
        bin.view = Platform.newDataView(bytes)
        bin.end = bytes.size
        val dec = JbDecoder2(membersDict = membersDict)
        dec.view = bin
        dec.end = bytes.size
        return dec.decodeValueAt(0)
    }

    /** Encode a feature [AnyObject] through the tuple path and decode it back with [membersDict]. */
    private fun tupleRoundTrip(feature: AnyObject, membersDict: IBook? = null): AnyObject {
        val enc = JbEncoder2()
        @Suppress("UNCHECKED_CAST")
        val tupleBytes = enc.buildTupleFromMap(feature as MapProxy<String, *>)
        val dec = JbDecoder2(membersDict = membersDict)
        dec.mapBytes(tupleBytes)
        return dec.toAnyObject()
    }

    // -----------------------------------------------------------------------
    // Test 1: primitive members-ref (integer)
    // -----------------------------------------------------------------------

    /**
     * The encoder short-circuits the key `"score"` to members-ref index 0.
     * The decoder is given a members book that holds `42` at index 0.
     * After decoding the feature object, `decoded["score"]` must equal `42`.
     */
    @Test
    fun testPrimitiveMembersRef() {
        // Build feature
        val feature = AnyObject()
        feature["id"] = "test"
        feature["score"] = 99  // will be intercepted by the member encoder

        // Encode: replace "score" value with a reference to members slot 0
        val enc = JbEncoder2().withMemberEncoder(IMemberEncoder { path, pathEnd, _ ->
            if (pathEnd > 0 && path[pathEnd - 1] == "score") 0 else -1
        })
        @Suppress("UNCHECKED_CAST")
        val tupleBytes = enc.buildTupleFromMap(feature as MapProxy<String, *>)

        // Members book: slot 0 → 42
        val membersDict = ListDict(listOf(42))

        val dec = JbDecoder2(membersDict = membersDict)
        dec.mapBytes(tupleBytes)
        val decoded = dec.toAnyObject()

        val score = decoded["score"]
        assertNotNull(score, "score must be present in decoded feature")
        assertEquals(42, score, "score must be resolved from members book as 42")
    }

    // -----------------------------------------------------------------------
    // Test 2: geometry via members-ref (ByteArray TWKB)
    // -----------------------------------------------------------------------

    /**
     * The encoder short-circuits the key `"geometry"` to members-ref index 0.
     * The decoder is given a members book that holds the raw TWKB [ByteArray] of a [SpPoint]
     * at index 0 — exactly as it would be read from a PostgreSQL `bytea` column.
     * After decoding, `decoded["geometry"]` must be an [SpGeometry] with the original coordinates.
     */
    @Test
    fun testGeometryMembersRefFromByteArray() {
        val lon = 13.4050
        val lat = 52.5200

        // Produce the TWKB bytes that would be stored in the PostgreSQL geo column
        val twkbBytes = GeoUtil.toTWKB(SpPoint(lon, lat))!!
        assertTrue(twkbBytes.isNotEmpty(), "TWKB bytes must not be empty")

        // Build feature — geometry value doesn't matter, it will be short-circuited
        val feature = AnyObject()
        feature["id"] = "berlin"
        feature["geometry"] = SpPoint(0.0, 0.0)  // placeholder; intercepted below

        // Encode: replace "geometry" value with a reference to members slot 0
        val enc = JbEncoder2().withMemberEncoder(IMemberEncoder { path, pathEnd, _ ->
            if (pathEnd > 0 && path[pathEnd - 1] == "geometry") 0 else -1
        })
        @Suppress("UNCHECKED_CAST")
        val tupleBytes = enc.buildTupleFromMap(feature as MapProxy<String, *>)

        // Members book: slot 0 → raw TWKB ByteArray (simulating PostgreSQL bytea column)
        val membersDict = ListDict(listOf(twkbBytes))

        val dec = JbDecoder2(membersDict = membersDict)
        dec.mapBytes(tupleBytes)
        val decoded = dec.toAnyObject()

        val geom = decoded["geometry"]
        assertNotNull(geom, "geometry must be present in decoded feature")
        assertTrue(geom is SpGeometry,
            "geometry from ByteArray members-ref must decode as SpGeometry, was: ${geom!!::class}")

        val decodedPoint = geom as SpPoint
        val coords = decodedPoint.getCoordinates()
        assertEquals(lon, coords.getLongitude(), 1e-5, "decoded longitude must match")
        assertEquals(lat, coords.getLatitude(),  1e-5, "decoded latitude must match")
    }

    // -----------------------------------------------------------------------
    // Test 3: string value via members-ref
    // -----------------------------------------------------------------------

    /**
     * Members book entry is a plain [String] (e.g. a `text` column from PostgreSQL).
     * The decoder must return it directly without any TWKB conversion.
     */
    @Test
    fun testStringMembersRef() {
        val feature = AnyObject()
        feature["id"] = "x"
        feature["label"] = "placeholder"

        val enc = JbEncoder2().withMemberEncoder(IMemberEncoder { path, pathEnd, _ ->
            if (pathEnd > 0 && path[pathEnd - 1] == "label") 0 else -1
        })
        @Suppress("UNCHECKED_CAST")
        val tupleBytes = enc.buildTupleFromMap(feature as MapProxy<String, *>)

        val membersDict = ListDict(listOf("hello world"))

        val dec = JbDecoder2(membersDict = membersDict)
        dec.mapBytes(tupleBytes)
        val decoded = dec.toAnyObject()

        assertEquals("hello world", decoded["label"], "label must resolve to the string from the members book")
    }

    // -----------------------------------------------------------------------
    // Test 4: null members slot
    // -----------------------------------------------------------------------

    /**
     * If the members book returns `null` for a slot, the decoded value must be `null`.
     */
    @Test
    fun testNullMembersRef() {
        val feature = AnyObject()
        feature["id"] = "x"
        feature["optional"] = "placeholder"

        val enc = JbEncoder2().withMemberEncoder(IMemberEncoder { path, pathEnd, _ ->
            if (pathEnd > 0 && path[pathEnd - 1] == "optional") 0 else -1
        })
        @Suppress("UNCHECKED_CAST")
        val tupleBytes = enc.buildTupleFromMap(feature as MapProxy<String, *>)

        val membersDict = ListDict(listOf(null))

        val dec = JbDecoder2(membersDict = membersDict)
        dec.mapBytes(tupleBytes)
        val decoded = dec.toAnyObject()

        // A null members entry should resolve to null, meaning the key may be absent or null.
        // The key was written with a ref, so it is present; the value resolves to null.
        val v = decoded["optional"]
        assertTrue(v == null, "optional must resolve to null when members slot is null, was: $v")
    }

    // -----------------------------------------------------------------------
    // Test 5: no membersDict supplied — ref resolves to null
    // -----------------------------------------------------------------------

    /**
     * When no [membersDict] is provided to the decoder, a [JB2_REF_BOOK_MEMBERS] reference
     * resolves to `null` rather than throwing.
     */
    @Test
    fun testMembersRefWithoutDict() {
        val feature = AnyObject()
        feature["id"] = "x"
        feature["score"] = 99

        val enc = JbEncoder2().withMemberEncoder(IMemberEncoder { path, pathEnd, _ ->
            if (pathEnd > 0 && path[pathEnd - 1] == "score") 0 else -1
        })
        @Suppress("UNCHECKED_CAST")
        val tupleBytes = enc.buildTupleFromMap(feature as MapProxy<String, *>)

        // Decode without membersDict
        val dec = JbDecoder2()
        dec.mapBytes(tupleBytes)
        val decoded = dec.toAnyObject()

        val score = decoded["score"]
        assertTrue(score == null, "score must be null when no membersDict is supplied, was: $score")
    }
}
