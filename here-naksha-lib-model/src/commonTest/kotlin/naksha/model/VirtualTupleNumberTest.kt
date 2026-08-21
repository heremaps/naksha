package naksha.model

import naksha.base.Action
import naksha.base.Version
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class VirtualTupleNumberTest {
    @Test
    fun equalInputsProduceEqualTupleNumbers() {
        val version = Version.virtualVersion(Action.VERSION)

        val first = Naksha.virtualTupleNumber("http-storage", "0", "collection", "feature", version)
        val second = Naksha.virtualTupleNumber("http-storage", "0", "collection", "feature", version)

        assertEquals(first, second)
        assertEquals(0, first.catalogNumber)
        assertEquals(version.number, first.version)
    }

    @Test
    fun identifiersDefineTupleScope() {
        val version = Version.virtualVersion(Action.VERSION)
        val base = Naksha.virtualTupleNumber("storage-a", "0", "collection-a", "feature-a", version)

        assertNotEquals(base.databaseNumber, Naksha.databaseNumber("storage-b"))
        assertNotEquals(base.collectionNumber, Naksha.collectionNumber("collection-b"))
        assertNotEquals(base.featureNumber, Naksha.featureNumber("feature-b"))
    }
}
