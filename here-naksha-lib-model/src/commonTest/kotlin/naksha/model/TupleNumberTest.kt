package naksha.model

import naksha.base.Int64
import naksha.model.TupleNumberVariant.TupleNumberVariant_C.B64
import naksha.model.TupleNumberVariant.TupleNumberVariant_C.B128
import naksha.model.TupleNumberVariant.TupleNumberVariant_C.B160
import naksha.model.TupleNumberVariant.TupleNumberVariant_C.B192
import naksha.model.TupleNumberVariant.TupleNumberVariant_C.B256
import kotlin.test.Test
import kotlin.test.assertEquals

class TupleNumberTest {

    private val storageNumber = Int64(1L)
    private val mapNumber = 2
    private val collectionNumber = 3
    private val featureNumber = Int64(0x12345678_9ABCDEF0L)
    // txn value with lower 2 bits clear so we can OR in the action bits
    private val txnBase = Int64(0x0102_0304_0506_0708L and -4L)

    /** Build a TupleNumber with the given action encoded in the lower 2 bits of txn.
     *  action.intValue is the raw 2-bit value (0=CREATED, 1=UPDATED, 2=DELETED). */
    private fun tn(action: Action): TupleNumber {
        val txn = txnBase or Int64(action.intValue.toLong())
        return TupleNumber(storageNumber, mapNumber, collectionNumber, featureNumber, txn)
    }

    @Test
    fun actionCreatedEncodedInTxn() {
        val t = tn(Action.CREATE)
        assertEquals(Action.CREATE, t.action)
    }

    @Test
    fun actionUpdatedEncodedInTxn() {
        val t = tn(Action.UPDATE)
        assertEquals(Action.UPDATE, t.action)
    }

    @Test
    fun actionDeletedEncodedInTxn() {
        val t = tn(Action.DELETE)
        assertEquals(Action.DELETE, t.action)
    }

    @Test
    fun binaryRoundTripB64() {
        val t = tn(Action.CREATE)
        val bytes = t.toByteArray(B64)
        assertEquals(8, bytes.size)
        val restored = TupleNumber.fromB64(bytes, storageNumber, mapNumber, collectionNumber, featureNumber)
        assertEquals(t.version, restored.version)
        assertEquals(t.action, restored.action)
    }

    @Test
    fun binaryRoundTripB128() {
        val t = tn(Action.UPDATE)
        val bytes = t.toByteArray(B128)
        assertEquals(16, bytes.size)
        val restored = TupleNumber.fromB128(bytes, storageNumber, mapNumber, collectionNumber)
        assertEquals(t.featureNumber, restored.featureNumber)
        assertEquals(t.version, restored.version)
        assertEquals(t.action, restored.action)
    }

    @Test
    fun binaryRoundTripB160() {
        val t = tn(Action.DELETE)
        val bytes = t.toByteArray(B160)
        assertEquals(20, bytes.size)
        val restored = TupleNumber.fromB160(bytes, storageNumber, mapNumber)
        assertEquals(t.collectionNumber, restored.collectionNumber)
        assertEquals(t.featureNumber, restored.featureNumber)
        assertEquals(t.version, restored.version)
        assertEquals(t.action, restored.action)
    }

    @Test
    fun binaryRoundTripB192() {
        val t = tn(Action.CREATE)
        val bytes = t.toByteArray(B192)
        assertEquals(24, bytes.size)
        val restored = TupleNumber.fromB192(bytes, storageNumber)
        assertEquals(t.catalogNumber, restored.catalogNumber)
        assertEquals(t.collectionNumber, restored.collectionNumber)
        assertEquals(t.featureNumber, restored.featureNumber)
        assertEquals(t.version, restored.version)
    }

    @Test
    fun binaryRoundTripB256() {
        val t = tn(Action.UPDATE)
        val bytes = t.toByteArray(B256)
        assertEquals(32, bytes.size)
        val restored = TupleNumber.fromB256(bytes)
        assertEquals(t.databaseNumber, restored.databaseNumber)
        assertEquals(t.catalogNumber, restored.catalogNumber)
        assertEquals(t.collectionNumber, restored.collectionNumber)
        assertEquals(t.featureNumber, restored.featureNumber)
        assertEquals(t.version, restored.version)
    }

    @Test
    fun stringRoundTrip() {
        val t = tn(Action.CREATE)
        val s = t.toString()
        val parts = s.split(":")
        // 5 parts: storageNumber, mapNumber, collectionNumber, featureNumber, version
        assertEquals(5, parts.size)
    }
}
