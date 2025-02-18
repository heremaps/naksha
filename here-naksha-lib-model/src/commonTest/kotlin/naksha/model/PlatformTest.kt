package naksha.model

import naksha.model.Naksha.NakshaCompanion.featureNumber
import naksha.model.Naksha.NakshaCompanion.hashId
import naksha.model.Naksha.NakshaCompanion.partitionNumber
import kotlin.test.Test
import kotlin.test.assertEquals

class PlatformTest {

    @Test
    fun testPartitionNumber() {
        // expect
        val collectionPartitions = 128
        assertEquals(44, partitionNumber(featureNumber(hashId("foo"))) % collectionPartitions)
        assertEquals(127, partitionNumber(featureNumber(hashId("fooA"))) % collectionPartitions)
        assertEquals(19, partitionNumber(featureNumber(hashId("fooB"))) % collectionPartitions)
        assertEquals(39, partitionNumber(featureNumber(hashId("fooC"))) % collectionPartitions)
        assertEquals(70, partitionNumber(featureNumber(hashId("fooD"))) % collectionPartitions)
    }

}