package naksha.model

import kotlin.test.Test
import kotlin.test.assertEquals

class PlatformTest {

    @Test
    fun testPartitionNumber() {
        // expect
        val collectionPartitions = 128
        assertEquals(44, Naksha.partitionNumber("foo") % collectionPartitions)
        assertEquals(127, Naksha.partitionNumber("fooA") % collectionPartitions)
        assertEquals(19, Naksha.partitionNumber("fooB") % collectionPartitions)
        assertEquals(39, Naksha.partitionNumber("fooC") % collectionPartitions)
        assertEquals(70, Naksha.partitionNumber("fooD") % collectionPartitions)
    }

}