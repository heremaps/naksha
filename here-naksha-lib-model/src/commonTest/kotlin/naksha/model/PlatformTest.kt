package naksha.model

import naksha.model.Naksha.NakshaCompanion.featureNumber
import naksha.model.Naksha.NakshaCompanion.partitionNumber
import kotlin.test.Test
import kotlin.test.assertEquals

class PlatformTest {

    @Test
    fun testPartitionNumber() {
        // expect
        val collectionPartitions = 128
        assertEquals(44, partitionNumber(featureNumber("foo")) % collectionPartitions)
        assertEquals(127, partitionNumber(featureNumber("fooA")) % collectionPartitions)
        assertEquals(19, partitionNumber(featureNumber("fooB")) % collectionPartitions)
        assertEquals(39, partitionNumber(featureNumber("fooC")) % collectionPartitions)
        assertEquals(70, partitionNumber(featureNumber("fooD")) % collectionPartitions)
    }

}