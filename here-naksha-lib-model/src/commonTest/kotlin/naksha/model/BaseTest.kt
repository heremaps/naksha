package naksha.model

import naksha.model.Naksha.NakshaCompanion.featureNumber
import naksha.model.Naksha.NakshaCompanion.partitionNumber
import kotlin.test.Test
import kotlin.test.assertEquals

class BaseTest {

    @Test
    fun testPartitionNumber() {
        // expect
        val collectionPartitions = 128
        assertEquals(88, partitionNumber(featureNumber("foo")) % collectionPartitions)
        assertEquals(99, partitionNumber(featureNumber("fooA")) % collectionPartitions)
        assertEquals(58, partitionNumber(featureNumber("fooB")) % collectionPartitions)
        assertEquals(72, partitionNumber(featureNumber("fooC")) % collectionPartitions)
        assertEquals(12, partitionNumber(featureNumber("fooD")) % collectionPartitions)
    }

}