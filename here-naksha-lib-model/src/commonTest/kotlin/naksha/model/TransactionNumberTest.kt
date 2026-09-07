package naksha.model

import naksha.base.Action
import naksha.base.Version
import kotlin.test.Test
import kotlin.test.assertEquals

class TransactionNumberTest {
    @Test
    fun versionFromToString() {
        val v1 = Version.auto(2024, 5, 10, 12345L, Action.VERSION)
        val s = v1.toString()
        val parsed = Version.fromString(s)
        assertEquals(v1.number, parsed.number)
    }
}
