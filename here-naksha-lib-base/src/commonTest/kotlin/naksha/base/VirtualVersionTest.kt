package naksha.base

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class VirtualVersionTest {
    @Test
    fun virtualVersionsAreDatedAndEncodeTheirAction() {
        val version = Version.virtualVersion(Action.VERSION)

        assertTrue(version.isDated())
        assertEquals(Action.VERSION, version.action())
    }

    @Test
    fun successiveVirtualVersionsAreDifferent() {
        val first = Version.virtualVersion(Action.VERSION)
        val second = Version.virtualVersion(Action.VERSION)

        assertNotEquals(first.number, second.number)
    }
}
