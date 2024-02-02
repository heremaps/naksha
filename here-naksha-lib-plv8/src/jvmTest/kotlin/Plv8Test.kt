import com.here.naksha.lib.plv8.Plv8Session
import org.junit.jupiter.api.*
import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.assertEquals

class Plv8Test : Plv8TestContainer() {

    @Order(2)
    @Test
    fun queryVersion() {
        val session = Plv8Session.get()
        val map = session.map()
        val sql = session.sql()
        val result = sql.execute("select naksha_version() as version")
        val rs = result.rows()
        for (raw in rs) {
            val row = raw as HashMap<String, Any?>
            assertEquals(1, row.size)
            assertEquals(0L, row["version"])
            assertEquals(1, map.size(raw))
            assertEquals(0L, map.get(raw, "version"))
        }
    }
}