package naksha.psql

import naksha.base.Epoch
import naksha.model.SessionOptions
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.request.*
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PartitioningTest : PgTestBase() {

    // TODO: Adjust this test to change the partition naming (low priority for now)
    // Naming changes from "feature_partitioned$hst$y2025$p001" to "feature_partitioned$hst$2025$1", similar "feature_partitioned$p001" -> "feature_partitioned$1" (etc.)
    @Ignore
    @Test
    fun createCollectionWithPartitions() {
        // given
        val numberOfPartitions = 8
        val partitionedCollection = NakshaCollection(
            id = "feature_partitioned",
            catalogId = catalog.id,
            partitions = numberOfPartitions
        )
        val writeOp = Write().createCollection(partitionedCollection)
        val writeRequest = WriteRequest().add(writeOp)

        // when
        executeWrite(writeRequest)

        // then
        val createdPartitions = queryForTablePartitions(partitionedCollection.id)
        assertEquals(numberOfPartitions, createdPartitions.size)
        for ((idx, createdPartition) in createdPartitions.withIndex()) {
            // "feature_partitioned$p000", "feature_partitioned$p001",...
            val expectedPartitionTableName = "\"feature_partitioned\$p${PgUtil.partitionSuffix(idx)}\""
            assertEquals(expectedPartitionTableName, createdPartition)
        }

        // also: check history partitioning
        val hstTable = "${partitionedCollection.id}${PG_HST}"
        val createdHstPartitions = queryForTablePartitions(hstTable)
        // first current year partition: like "feature_partitioned$hst$2025"
        assertEquals("\"feature_partitioned\$hst\$${Epoch().year}\"", createdHstPartitions[0])
        // next year
        assertEquals("\"feature_partitioned\$hst\$${Epoch().year+1}\"", createdHstPartitions[1])
        for (hstPartition in createdHstPartitions) {
            val rawHstName = hstPartition.replace("\"", "")
            val createdHstSubPartitions = queryForTablePartitions(rawHstName)
            assertEquals(numberOfPartitions, createdHstSubPartitions.size)
            for ((idx, createdPartition) in createdHstSubPartitions.withIndex()) {
                // "feature_partitioned$hst$y2025$p001", ...
                val expectedPartitionTableName = "\"$rawHstName\$p${PgUtil.partitionSuffix(idx)}\""
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
            catalogId = catalog.id,
            partitions = numberOfPartitions
        )
        val writeOp = Write().createCollection(partitionedCollection)
        val writeRequest = WriteRequest().add(writeOp)
        newWriteSession().use { session ->
            session.execute(writeRequest)
            session.commit()
        }

        // when
        val f1 = NakshaFeature("f1")
        val writeFeatureOp = Write().createFeature(partitionedCollection, f1)
        val writeFeatureRequest = WriteRequest().add(writeFeatureOp)
        newWriteSession().use { session ->
            val result = session.execute(writeFeatureRequest)
            session.commit()

            // then
            // feature should be successfully stored
            assertTrue { result is SuccessResponse }
            assertEquals(1, (result as SuccessResponse).features.size)
        }

        // also - should be able to read
        val readRequest = ReadFeatures()
        readRequest.catalogId = partitionedCollection.catalogId
        readRequest.collectionId = partitionedCollection.id
        readRequest.featureIds.add("f1")
        val readResponse = executeRead(readRequest)
        assertEquals(1, readResponse.features.size)
    }

    @Test
    fun shouldAllowZeroPartitions() {
        // given
        val numberOfPartitions = 0
        val partitionedCollection = NakshaCollection(
            id = "zero_partitions",
            catalogId = catalog.id,
            partitions = numberOfPartitions
        )
        val writeOp = Write().createCollection(partitionedCollection)
        val writeRequest = WriteRequest().add(writeOp)

        // when
        val response = executeWrite(writeRequest)

        // then
        assertEquals(1, response.features.size)
    }

    @Test
    fun shouldAllowOnePartitions() {
        // given
        val numberOfPartitions = 1
        val partitionedCollection = NakshaCollection(
            id = "one_partitions",
            catalogId = catalog.id,
            partitions = numberOfPartitions
        )
        val writeOp = Write().createCollection(partitionedCollection)
        val writeRequest = WriteRequest().add(writeOp)

        // when
        val response = executeWrite(writeRequest)

        // then
        assertEquals(1, response.features.size)
    }

    @Test
    fun shouldNotAllowMoreThan1000Partitions() {
        // given
        val numberOfPartitions = 65536
        val partitionedCollection = NakshaCollection(
            id = "to_many_partitions",
            catalogId = catalog.id,
            partitions = numberOfPartitions
        )
        val writeOp = Write().createCollection(partitionedCollection)
        val writeRequest = WriteRequest().add(writeOp)

        // when
        newWriteSession().use { session ->
            // expect
            val response = session.execute(writeRequest) as ErrorResponse
            assertEquals("Invalid partition-count, expect 2 .. 1000, found : 65536", response.error.msg)
        }
    }

    private fun queryForTablePartitions(table: String): List<String> {
        storage.newConnection(SessionOptions.from(null), true).use { pgConnection ->
            pgConnection.execute("""
SET search_path TO "${catalog.id}", "naksha~admin", topology, hint_plan, public;
SELECT inhrelid::regclass AS partitioned_table FROM pg_inherits WHERE inhparent = $1::regclass ORDER BY partitioned_table;
""",
                arrayOf(table)
            ).use { cursor ->
                val result = mutableListOf<String>()
                while (cursor.next()) {
                    result.add(cursor.column("partitioned_table").toString())
                }
                return result
            }
        }
    }
}