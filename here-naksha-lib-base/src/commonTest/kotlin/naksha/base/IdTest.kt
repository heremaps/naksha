package naksha.base

import naksha.base.Id.IdCompanion.ADMIN_CATALOG_INT
import naksha.base.Id.IdCompanion.ADMIN_CATALOG_NUMBER
import naksha.base.Id.IdCompanion.ADMIN_CATALOG_TEXT
import naksha.base.Id.IdCompanion.BOOKS_COL_INT
import naksha.base.Id.IdCompanion.BOOKS_COL_NUMBER
import naksha.base.Id.IdCompanion.BOOKS_COL_TEXT
import naksha.base.Id.IdCompanion.CATALOGS_COL_INT
import naksha.base.Id.IdCompanion.CATALOGS_COL_NUMBER
import naksha.base.Id.IdCompanion.CATALOGS_COL_TEXT
import naksha.base.Id.IdCompanion.COLLECTIONS_COL_INT
import naksha.base.Id.IdCompanion.COLLECTIONS_COL_NUMBER
import naksha.base.Id.IdCompanion.COLLECTIONS_COL_TEXT
import naksha.base.Id.IdCompanion.TRANSACTIONS_COL_INT
import naksha.base.Id.IdCompanion.TRANSACTIONS_COL_NUMBER
import naksha.base.Id.IdCompanion.TRANSACTIONS_COL_TEXT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IdTest {
    @Test
    fun partitionIndicesMatchEveryRangeBoundary() {
        for (partitions in 1..256) {
            val sizes = IntArray(partitions)
            for (byte in 0..255) {
                val index = Id(byte.toLong()).partitionIndex(partitions)
                assertTrue(index in 0 until partitions)
                assertTrue(byte >= index * 256 / partitions)
                assertTrue(byte < (index + 1) * 256 / partitions)
                sizes[index]++
                for (upperBits in listOf(0L, Long.MIN_VALUE, -256L)) {
                    val fn = upperBits or byte.toLong()
                    assertEquals(index, Id.partitionIndex(fn, partitions))
                    assertEquals(index, TupleNumber(1L, 1, 1, fn, 0L).partitionIndex(partitions))
                }
            }
            assertTrue(sizes.all { it > 0 })
            assertTrue(sizes.max() - sizes.min() <= 1)
        }
    }

    @Test
    fun partitionIndicesRejectInvalidCounts() {
        for (partitions in listOf(Int.MIN_VALUE, -1, 0, 257, Int.MAX_VALUE)) {
            assertFailsWith<NakshaException> { Id(1L).partitionIndex(partitions) }
            assertFailsWith<NakshaException> { TupleNumber(1L, 1, 1, 1L, 0L).partitionIndex(partitions) }
        }
    }

    @Test
    fun ordinalBlocksTileTheSpaceMonotonically() {
        assertEquals(Long.MIN_VALUE, Id.partitionMinOrd(0))
        assertEquals(Long.MAX_VALUE, Id.partitionMaxOrd(255))
        var previousMax: Long? = null
        for (partitionNumber in 0..255) {
            val min = Id.partitionMinOrd(partitionNumber)
            val max = Id.partitionMaxOrd(partitionNumber)
            assertEquals(1L shl 56, max - min + 1)
            if (previousMax != null) assertEquals(previousMax + 1, min)
            previousMax = max
        }
        assertEquals(Long.MAX_VALUE, previousMax)
    }

    @Test
    fun partitionNumberRangesTileTheSpace() {
        for (partitions in 1..256) {
            assertEquals(0, Id.partitionNumberMin(0, partitions))
            assertEquals(255, Id.partitionNumberMax(partitions - 1, partitions))
            for (index in 1 until partitions) {
                assertEquals(Id.partitionNumberMax(index - 1, partitions) + 1, Id.partitionNumberMin(index, partitions))
            }
        }
    }

    @Test
    fun partitionIndexConsistentWithNumberMinAndMax() {
        for (partitions in 1..256) {
            for (partitionNumber in 0..255) {
                val index = Id.partitionIndex(partitionNumber.toLong(), partitions)
                assertTrue(Id.partitionNumberMin(index, partitions) <= partitionNumber)
                assertTrue(partitionNumber <= Id.partitionNumberMax(index, partitions))
            }
        }
    }

    @Test
    fun partitionIndexBoundsAreContiguousAcrossIndexes() {
        for (partitions in 1..256) {
            for (index in 1 until partitions) {
                assertEquals(Id.partitionIndexMax(index - 1, partitions) + 1, Id.partitionIndexMin(index, partitions))
            }
        }
    }

    @Test
    fun sevenPartitionsProduceDocumentedRanges() {
        val expectedMin = listOf(
            Long.MIN_VALUE, -6629298651489370112L, -3963167672086036480L,
            -1369094286720630784L, 1297036692682702848L, 3891110078048108544L, 6557241057451442176L
        )
        val expectedMax = listOf(
            -6629298651489370113L, -3963167672086036481L, -1369094286720630785L,
            1297036692682702847L, 3891110078048108543L, 6557241057451442175L, 9223372036854775807L
        )
        for (index in 0..6) {
            assertEquals(expectedMin[index], Id.partitionIndexMin(index, 7))
            assertEquals(expectedMax[index], Id.partitionIndexMax(index, 7))
        }
        assertEquals(0, Id.partitionNumberMin(0, 7))
        assertEquals(35, Id.partitionNumberMax(0, 7))
        assertEquals(73, Id.partitionNumberMin(2, 7))
        assertEquals(108, Id.partitionNumberMax(2, 7))
        assertEquals(219, Id.partitionNumberMin(6, 7))
        assertEquals(255, Id.partitionNumberMax(6, 7))
    }

    @Test
    fun instancePartitionMethodsMatchCompanionMethods() {
        // Id(number) only accepts non-negative numbers; negative (hashed) feature-numbers are
        // covered at the companion level by the other tests. High bits must not affect routing.
        val featureNumbers = (0L..255L).toList() + listOf(256L, 1L shl 40, 1L shl 56, Long.MAX_VALUE)
        for (partitions in listOf(1, 2, 7, 16, 255, 256)) {
            for (featureNumber in featureNumbers) {
                val id = Id(featureNumber)
                val index = id.partitionIndex(partitions)
                assertEquals(featureNumber.toInt() and 0xff, id.partitionNumber)
                assertEquals(Id.featureOrdinal(featureNumber), id.featureOrdinal)
                assertEquals(Id.partitionMinOrd(id.partitionNumber), id.partitionMinOrd)
                assertEquals(Id.partitionMaxOrd(id.partitionNumber), id.partitionMaxOrd)
                assertEquals(Id.partitionNumberMin(index, partitions), id.partitionNumberMin(partitions))
                assertEquals(Id.partitionNumberMax(index, partitions), id.partitionNumberMax(partitions))
                assertEquals(Id.partitionIndexMin(index, partitions), id.partitionIndexMin(partitions))
                assertEquals(Id.partitionIndexMax(index, partitions), id.partitionIndexMax(partitions))
            }
        }
    }

    @Test
    fun partitionIndexBoundsRejectInvalidIndexes() {
        for (index in listOf(-1, 7, 256)) {
            assertFailsWith<NakshaException> { Id.partitionNumberMin(index, 7) }
            assertFailsWith<NakshaException> { Id.partitionNumberMax(index, 7) }
            assertFailsWith<NakshaException> { Id.partitionIndexMin(index, 7) }
            assertFailsWith<NakshaException> { Id.partitionIndexMax(index, 7) }
        }
    }

    @Test
    fun predefinedCollectionNamesHaveFixedNumbers() {
        // naksha~admin
        assertEquals(ADMIN_CATALOG_NUMBER, Id(ADMIN_CATALOG_TEXT).number)
        assertEquals(ADMIN_CATALOG_INT, Id(ADMIN_CATALOG_TEXT).intValue)

        // naksha~collections
        assertEquals(COLLECTIONS_COL_NUMBER, Id(COLLECTIONS_COL_TEXT).number)
        assertEquals(COLLECTIONS_COL_INT, Id(COLLECTIONS_COL_TEXT).intValue)

        // naksha~catalogs
        assertEquals(CATALOGS_COL_NUMBER, Id(CATALOGS_COL_TEXT).number)
        assertEquals(CATALOGS_COL_INT, Id(CATALOGS_COL_TEXT).intValue)

        // naksha~transactions
        assertEquals(TRANSACTIONS_COL_NUMBER, Id(TRANSACTIONS_COL_TEXT).number)
        assertEquals(TRANSACTIONS_COL_INT, Id(TRANSACTIONS_COL_TEXT).intValue)

        // naksha~books
        assertEquals(BOOKS_COL_NUMBER, Id(BOOKS_COL_TEXT).number)
        assertEquals(BOOKS_COL_INT, Id(BOOKS_COL_TEXT).intValue)
    }

    @Test
    fun ensureReversingPredefinedIds() {
        assertEquals(ADMIN_CATALOG_TEXT, Id.numberToText(ADMIN_CATALOG_NUMBER))
        assertEquals(COLLECTIONS_COL_TEXT, Id.numberToText(COLLECTIONS_COL_NUMBER))
        assertEquals(CATALOGS_COL_TEXT, Id.numberToText(CATALOGS_COL_NUMBER))
        assertEquals(TRANSACTIONS_COL_TEXT, Id.numberToText(TRANSACTIONS_COL_NUMBER))
        assertEquals(BOOKS_COL_TEXT, Id.numberToText(BOOKS_COL_NUMBER))
    }
}
