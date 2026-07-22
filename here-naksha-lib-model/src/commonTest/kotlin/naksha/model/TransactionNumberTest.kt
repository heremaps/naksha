package naksha.model

import naksha.base.Int64
import kotlin.test.Test
import kotlin.test.assertEquals

class TransactionNumberTest {
    @Test
    fun versionFromToString() {
        val v1 = Version.of(2024, 5, 10, Int64(12345))
        val s = v1.toString()
        val parsed = Version.fromString(s)
        assertEquals(v1.txn, parsed.txn)
    }
}