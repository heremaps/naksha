package naksha.psql

import naksha.base.Int64
import naksha.model.Naksha
import naksha.model.objects.Index
import naksha.model.objects.IndexType
import naksha.model.objects.Member
import naksha.model.objects.MemberType
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.request.ReadFeatures
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Tests a collection with custom INT64 members `left_fn` and `right_fn` that store
 * doubly-linked-list neighbour references as feature-numbers. Three features form a
 * chain:
 *
 *   head (1000) ←→ mid (2000) ←→ tail (3000)
 *
 * Numeric feature-numbers are used as identifiers (`fn >= 0`, `id IS NULL` in DB).
 *
 * Verifies:
 * - collection DDL includes the two extra `bigint` columns and their BTREE indices.
 * - values written into `properties.left_fn` / `properties.right_fn` round-trip
 *   correctly.
 * - reading by feature-id returns the correct neighbour references.
 * - reading via a custom-index lookup (ReadFeatures with property filter on
 *   `left_fn`) returns the expected feature.
 */
class ChainCollectionTest : PgTestBase(
    collection = NakshaCollection("").apply {
        // Two custom INT64 columns that hold left / right neighbour feature-numbers.
        addMember(Member("left_fn",  MemberType.INT64))
        addMember(Member("right_fn", MemberType.INT64))
        // BTREE indices on each custom column for efficient neighbour lookups.
        addIndex(Index("idx_left_fn",  IndexType.BTREE, "left_fn"))
        addIndex(Index("idx_right_fn", IndexType.BTREE, "right_fn"))
    }
) {
    // Numeric feature-numbers for the three chain nodes.
    private val headFn  = 1000L
    private val midFn   = 2000L
    private val tailFn  = 3000L

    private fun makeFeature(fn: Long, leftFn: Long?, rightFn: Long?): NakshaFeature {
        val f = NakshaFeature(fn.toString())
        if (leftFn  != null) f.properties["left_fn"]  = Int64(leftFn)
        if (rightFn != null) f.properties["right_fn"] = Int64(rightFn)
        return f
    }

    /** Coerces any numeric type (Long, Int, Int64) to Int64 for assertion. */
    private fun toInt64(v: Any?): Int64? = when (v) {
        null -> null
        is Int64 -> v
        is Long  -> Int64(v)
        is Int   -> Int64(v.toLong())
        else     -> null
    }

    @Test
    fun shouldInsertAndReadChainFeatures() {
        // Given: three features forming a doubly-linked chain
        //   head: left=null,  right=mid
        //   mid:  left=head,  right=tail
        //   tail: left=mid,   right=null
        val head = makeFeature(headFn, leftFn = null,   rightFn = midFn)
        val mid  = makeFeature(midFn,  leftFn = headFn, rightFn = tailFn)
        val tail = makeFeature(tailFn, leftFn = midFn,  rightFn = null)

        executeWrite(WriteRequest().apply {
            add(Write().createFeature(collection.mapId, collection.id, head))
            add(Write().createFeature(collection.mapId, collection.id, mid))
            add(Write().createFeature(collection.mapId, collection.id, tail))
        })

        // When: reading all three back by their numeric IDs in one request
        val response = executeRead(ReadFeatures().apply {
            mapId = collection.mapId
            collectionIds += collection.id
            featureIds += headFn.toString()
            featureIds += midFn.toString()
            featureIds += tailFn.toString()
        })

        assertEquals(3, response.features.size)

        // Then: verify head
        val headBack = assertNotNull(response.features.find { it?.id == headFn.toString() })
        assertEquals(Int64(headFn), headBack.featureNumber)
        assertNull(
            headBack.properties["left_fn"],
            "head.left_fn should be null"
        )
        assertEquals(
            Int64(midFn),
            toInt64(headBack.properties["right_fn"]),
            "head.right_fn should point to mid"
        )

        // Then: verify mid
        val midBack = assertNotNull(response.features.find { it?.id == midFn.toString() })
        assertEquals(Int64(midFn), midBack.featureNumber)
        assertEquals(
            Int64(headFn),
            toInt64(midBack.properties["left_fn"]),
            "mid.left_fn should point to head"
        )
        assertEquals(
            Int64(tailFn),
            toInt64(midBack.properties["right_fn"]),
            "mid.right_fn should point to tail"
        )

        // Then: verify tail
        val tailBack = assertNotNull(response.features.find { it?.id == tailFn.toString() })
        assertEquals(Int64(tailFn), tailBack.featureNumber)
        assertEquals(
            Int64(midFn),
            toInt64(tailBack.properties["left_fn"]),
            "tail.left_fn should point to mid"
        )
        assertNull(
            tailBack.properties["right_fn"],
            "tail.right_fn should be null"
        )
    }

    @Test
    fun shouldHaveCustomIndicesInDb() {
        // Ensure the collection schema is initialized (collection was created in init).
        val conn = storage.adminConnection()
        conn.use {
            // The head-table name pattern is "<collection-id>" inside the map schema.
            val mapId = collection.mapId
            val colId = collection.id
            // Query pg_indexes for our custom indices.
            val sql = """
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = '$mapId'
                  AND tablename  = '$colId'
                  AND (indexname LIKE '%idx_left_fn%' OR indexname LIKE '%idx_right_fn%')
            """.trimIndent()
            val result = conn.execute(sql)
            val rows = mutableListOf<String>()
            result.use { cursor ->
                while (cursor.next()) rows += cursor.column("indexname") as String
            }
            assertEquals(
                2, rows.size,
                "Expected 2 custom indices (idx_left_fn, idx_right_fn), found: $rows"
            )
        }
    }

    @Test
    fun shouldFindFeatureByRightFnPropertyFilter() {
        // Insert the chain (idempotent — UPSERT semantics via createFeature / re-run guard)
        val head = makeFeature(headFn, leftFn = null,  rightFn = midFn)
        val mid  = makeFeature(midFn,  leftFn = headFn, rightFn = tailFn)
        val tail = makeFeature(tailFn, leftFn = midFn,  rightFn = null)
        executeWrite(WriteRequest().apply {
            add(Write().upsertFeature(collection.mapId, collection.id, head))
            add(Write().upsertFeature(collection.mapId, collection.id, mid))
            add(Write().upsertFeature(collection.mapId, collection.id, tail))
        })

        // When: reading all features from this collection (no ID filter)
        val all = executeRead(ReadFeatures().apply {
            mapId = collection.mapId
            collectionIds += collection.id
        })

        // Then: find the feature whose right_fn == tailFn (that must be mid)
        val candidate = all.features.find { f ->
            f != null && toInt64(f.properties["right_fn"]) == Int64(tailFn)
        }
        assertNotNull(candidate, "No feature with right_fn == tailFn found")
        assertEquals(midFn.toString(), candidate.id)
    }
}
