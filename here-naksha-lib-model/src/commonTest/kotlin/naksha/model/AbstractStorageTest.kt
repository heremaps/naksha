package naksha.model

import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import naksha.base.Action
import naksha.base.Int64
import naksha.base.NakshaError.NakshaErrorCompanion.UNINITIALIZED
import naksha.base.NakshaException
import naksha.base.TupleNumber
import naksha.base.Version
import naksha.model.objects.NakshaStorage
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Clock

class AbstractStorageTest {

    @Test
    fun newVirtualVersionUsesCurrentUtcDateAndIncrementsSequence() {
        val storage = TestStorage()
        while (true) {
            val before = Clock.System.now().toLocalDateTime(TimeZone.UTC).date

            storage.resetNextVirtualVersion(Int64(3))
            val first = storage.newVersion()
            val second = storage.newVersion()

            val after = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
            if (before != after) continue // This should only happen when the test is executed exactly around midnight UTC!

            assertEquals(before.year, first.year)
            assertEquals(before.month.number, first.month)
            assertEquals(before.day, first.day)
            assertEquals(Action.VERSION, Action.fromValue(first.number.toInt() and 3))
            assertEquals(first.number + 4, second.number)
            break
        }
    }

    @Test
    fun newVirtualTupleNumberDerivesNumbersAndEncodesAction() {
        val storageId = "test-storage"
        val catalogId = "test-catalog"
        val collectionId = "test-collection"
        val featureId = "test-feature"
        val storage = TestStorage().apply { initialize(storageId) }
        val version = Version.auto(2026, 8, 24, Int64(42), Action.VERSION).number

        for (action in listOf(Action.CREATE, Action.UPDATE, Action.DELETE, Action.VERSION)) {
            val tupleNumber = storage.newTupleNumber(catalogId, collectionId, featureId, version, action)

            assertEquals(Naksha.databaseNumber(storageId), tupleNumber.databaseNumber)
            assertEquals(Naksha.catalogNumber(catalogId), tupleNumber.catalogNumber)
            assertEquals(Naksha.collectionNumber(collectionId), tupleNumber.collectionNumber)
            assertEquals(Naksha.featureNumber(featureId), tupleNumber.featureNumber)
            assertEquals(version.toLong() and -4L, tupleNumber.version.toLong() and -4L)
            assertEquals(action, tupleNumber.action)
        }
    }

    @Test
    fun newVirtualTupleNumberRequiresInitializedStorage() {
        val exception = assertFailsWith<NakshaException> {
            TestStorage().newTupleNumber("catalog", "collection", "feature", Int64(3), Action.VERSION)
        }

        assertEquals(UNINITIALIZED, exception.error.code)
    }

    private class TestStorage : AbstractStorage<NakshaStorage>() {
        override val configKlass: KClass<NakshaStorage> = NakshaStorage::class

        fun initialize(storageId: String) {
            invokeInitStorage(NakshaStorage(storageId, "TestStorage"), create = null, upgrade = null)
        }

        fun resetNextVirtualVersion(version: Int64) {
            nextVirtualVersion.set(version)
        }

        fun newVersion(): Version = newVirtualVersion()

        fun newTupleNumber(
            catalogId: String,
            collectionId: String,
            featureId: String,
            version: Int64,
            action: Action,
        ): TupleNumber = newVirtualTupleNumber(catalogId, collectionId, featureId, version, action)

        override fun initStorage(config: NakshaStorage, create: Boolean?, upgrade: Boolean?) = Unit
        override fun afterInit() = Unit
        override fun shutdownStorage(dropCache: Boolean) = Unit
        override fun newWriteSession(options: SessionOptions?): IWriteSession = error("Not used by this test")
        override fun newReadSession(options: SessionOptions?): IReadSession = error("Not used by this test")
    }
}
