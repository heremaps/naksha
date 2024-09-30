package naksha.psql

import naksha.base.Epoch
import naksha.model.SessionOptions
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.request.*
import naksha.psql.base.PgTestBase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class PartitioningTest : PgTestBase() {

    @Test
    fun createCollectionWithPartitions() {
        // given
        val numberOfPartitions = 8
        val partitionedCollection = NakshaCollection(
            id = "feature_partitioned",
            partitions = numberOfPartitions
        )
        val writeOp = Write().createCollection(map = null, collection = partitionedCollection)
        val writeRequest = WriteRequest().add(writeOp)

        // when
        storage.newWriteSession().use { session ->
            session.execute(writeRequest)
            session.commit()
        }

        // then
        val createdPartitions = queryForTablePartitions(partitionedCollection.id)
        assertEquals(numberOfPartitions, createdPartitions.size)
        for ((idx, createdPartition) in createdPartitions.withIndex()) {
            // "feature_partitioned$p000", "feature_partitioned$p001",...
            val expectedPartitionTableName = "\"feature_partitioned\$p${PgUtil.partitionPosix(idx)}\""
            assertEquals(expectedPartitionTableName, createdPartition)
        }

        // also: check history partitioning
        val hstTable = "${partitionedCollection.id}${PG_HST}"
        val createdHstPartitions = queryForTablePartitions(hstTable)
        // first current year partition: like "feature_partitioned$hst$y2025"
        assertEquals("\"feature_partitioned\$hst\$y${Epoch().year}\"", createdHstPartitions[0])
        // next year
        assertEquals("\"feature_partitioned\$hst\$y${Epoch().year+1}\"", createdHstPartitions[1])
        for (hstPartition in createdHstPartitions) {
            val rawHstName = hstPartition.replace("\"", "")
            val createdHstSubPartitions = queryForTablePartitions(rawHstName)
            assertEquals(numberOfPartitions, createdHstSubPartitions.size)
            for ((idx, createdPartition) in createdHstSubPartitions.withIndex()) {
                // "feature_partitioned$hst$y2025$p001", ...
                val expectedPartitionTableName = "\"$rawHstName\$p${PgUtil.partitionPosix(idx)}\""
                assertEquals(expectedPartitionTableName, createdPartition)
            }
        }
    }

    @Test
    fun shouldInsertToSpecificPartition() {
        // given
        val numberOfPartitions = 2
        val partitionedCollection = NakshaCollection(
            id = "feature_partitioned_insert_check",
            partitions = numberOfPartitions
        )
        val writeOp = Write().createCollection(map = null, collection = partitionedCollection)
        val writeRequest = WriteRequest().add(writeOp)
        storage.newWriteSession().use { session ->
            session.execute(writeRequest)
            session.commit()
        }

        // when
        val writeFeatureOp = Write().createFeature(null, partitionedCollection.id, NakshaFeature("f1"))
        val writeFeatureRequest = WriteRequest().add(writeFeatureOp)
        storage.newWriteSession().use { session ->
            val result = session.execute(writeFeatureRequest)
            session.commit()

            // then
            // feature should be successfully stored
            assertTrue { result is SuccessResponse }
            assertEquals(1, (result as SuccessResponse).tuples.size)
        }

        // also - should be able to read
        val readRequest = ReadFeatures(partitionedCollection.id)
        readRequest.featureIds.add("f1")
        val readResponse = storage.newWriteSession().execute(readRequest) as SuccessResponse
        assertEquals(1, readResponse.features.size)
    }

    @Test
    fun shouldNotAllowZeroPartitions() {
        // given
        val numberOfPartitions = 0
        val partitionedCollection = NakshaCollection(
            id = "zero_partitions",
            partitions = numberOfPartitions
        )
        val writeOp = Write().createCollection(map = null, collection = partitionedCollection)
        val writeRequest = WriteRequest().add(writeOp)

        // when
        storage.newWriteSession().use { session ->
            // expect
            assertFails("Invalid amount of partitions requested, must be 1 to 256, was: 0") { session.execute(writeRequest) }
        }
    }

    @Test
    fun shouldNotAllowMoreThan256Partitions() {
        // given
        val numberOfPartitions = 257
        val partitionedCollection = NakshaCollection(
            id = "to_many_partitions",
            partitions = numberOfPartitions
        )
        val writeOp = Write().createCollection(map = null, collection = partitionedCollection)
        val writeRequest = WriteRequest().add(writeOp)

        // when
        storage.newWriteSession().use { session ->
            // expect
            assertFails("Invalid amount of partitions requested, must be 1 to 256, was: 0") { session.execute(writeRequest) }
        }
    }

    private fun queryForTablePartitions(table: String): List<String> {
        val pgConnection = storage.newConnection(SessionOptions.from(null), true)
        val cursor = pgConnection.execute(
            "SELECT inhrelid::regclass AS partitioned_table FROM pg_inherits WHERE inhparent = $1::regclass order by partitioned_table",
            arrayOf(table)
        )
        val result = mutableListOf<String>()
        while (cursor.next()) {
            result.add(cursor.column("partitioned_table").toString())
        }
        return result
    }
}