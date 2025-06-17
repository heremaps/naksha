package naksha.model

import naksha.model.Naksha.Naksha_C.featureNumber
import naksha.model.Naksha.Naksha_C.partitionNumber
import kotlin.test.Test
import kotlin.test.assertEquals

class PlatformTest {

    @Test
    fun testPartitionNumber() {
        try {
            // expect
            val collectionPartitions = 128
            assertEquals(88, partitionNumber(featureNumber("foo")) % collectionPartitions)
            assertEquals(99, partitionNumber(featureNumber("fooA")) % collectionPartitions)
            assertEquals(58, partitionNumber(featureNumber("fooB")) % collectionPartitions)
            assertEquals(72, partitionNumber(featureNumber("fooC")) % collectionPartitions)
            assertEquals(12, partitionNumber(featureNumber("fooD")) % collectionPartitions)
        } catch (_: UnsupportedOperationException) {
            // TODO: Implement this in JavaScript!
        }
    }
}