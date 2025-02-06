package naksha.psql

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class PsqlInstanceTest {
    @Test
    fun testParsing() {
        val instance = PsqlInstance.get("jdbc:postgresql://localhost/unimap?user=postgres&password=secret")
        assertNotNull(instance)
        assertEquals("localhost", instance.host)
        assertEquals(5432, instance.port)
        assertEquals("unimap", instance.database)
        assertEquals("postgres", instance.user)
        assertEquals("secret", instance.password)
        assertFalse(instance.readOnly)
    }

    @Test
    fun testDeclaring() {
        val instance = PsqlInstance.get("localhost", 5432, "unimap", "postgres", "secret")
        assertNotNull(instance)
        assertEquals("localhost", instance.host)
        assertEquals(5432, instance.port)
        assertEquals("unimap", instance.database)
        assertEquals("postgres", instance.user)
        assertEquals("secret", instance.password)
        assertFalse(instance.readOnly)
    }
}