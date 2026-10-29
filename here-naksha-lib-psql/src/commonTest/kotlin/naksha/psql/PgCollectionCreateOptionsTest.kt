package naksha.psql

import naksha.base.NakshaException
import naksha.base.Version
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PgCollectionCreateOptionsTest {
    private fun options() = PgCollectionCreateOptions(
        true, 16, 37, 24, -1, 3, PgStorageClass.Consistent, null, 2048
    )

    @Test
    fun computesAlignedRangesWithoutIncludingHead() {
        val options = options()
        assertEquals(1L shl 37, options.interval)
        assertEquals((1L shl 37) * 24, options.retentionInterval)
        assertEquals(0L, options.historyRangeStart(options.interval - 1))
        assertEquals(options.interval, options.historyRangeStart(options.interval))
        assertEquals(options.interval, options.historyRangeEnd(0))
        assertEquals(Version.HEAD.number, options.historyRangeEnd(Version.HEAD.number - 1))
        assertFailsWith<NakshaException> { options.historyRangeStart(-1) }
        assertFailsWith<NakshaException> { options.historyRangeStart(Version.HEAD.number) }
    }

    @Test
    fun rejectsInvalidConfigurationEvenWithoutHistory() {
        val options = options().copy(storeHistory = false)
        for (partitions in listOf(0, -1, 257, Int.MAX_VALUE)) {
            assertFailsWith<NakshaException> { options.copy(partitions = partitions) }
        }
        for (shift in listOf(-1, 0, 11, 42, 64)) {
            assertFailsWith<NakshaException> { options.copy(shift = shift) }
        }
        assertFailsWith<NakshaException> { options.copy(retain = 1) }
        assertFailsWith<NakshaException> { options.copy(backward = 0) }
        assertFailsWith<NakshaException> { options.copy(backward = -24) }
        assertFailsWith<NakshaException> { options.copy(backward = Int.MIN_VALUE) }
        assertFailsWith<NakshaException> { options.copy(forward = -1) }
        assertFailsWith<NakshaException> { options.copy(forward = 24) }
        assertFailsWith<NakshaException> { options.copy(shift = 41, retain = Int.MAX_VALUE) }
        assertFailsWith<NakshaException> { options.copy(toastTupleTarget = 2047) }
        assertFailsWith<NakshaException> { options.copy(toastTupleTarget = 32737) }
        assertFailsWith<NakshaException> { options.copy(tablespace = "") }
        assertFailsWith<NakshaException> { options.copy(tablespace = "bad\u0000name") }
        assertFailsWith<NakshaException> { options.copy(storageClass = PgStorageClass.Unknown) }
    }

    @Test
    fun acceptsPartitionAndShiftBoundaries() {
        for (partitions in listOf(1, 3, 16, 256)) {
            for (shift in listOf(12, 37, 41)) {
                val options = options().copy(partitions = partitions, shift = shift, tablespace = "Mixed Space")
                assertEquals(partitions, options.partitions)
                assertEquals(1L shl shift, options.interval)
            }
        }
    }
}
