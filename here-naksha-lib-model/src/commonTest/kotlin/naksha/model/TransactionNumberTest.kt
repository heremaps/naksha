package naksha.model

import naksha.base.Action
import naksha.base.Int64
import naksha.base.Version
import kotlin.test.Test
import kotlin.test.assertEquals

class TransactionNumberTest {
    @Test
    fun versionFromToString() {
        val v1 = Version.auto(2024, 5, 10, Int64(12345), Action.VERSION)
        val s = v1.toString()
        val parsed = Version.fromString(s)
        assertEquals(v1.number, parsed.number)
    }
}