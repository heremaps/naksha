import com.here.naksha.lib.jbon.Jb
import com.here.naksha.lib.jbon.JbDictManager
import com.here.naksha.lib.plv8.NakshaSession
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.TestMethodOrder

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
open class JbTest {
    val env = Jb.env
    val dictManager = JbDictManager()

    internal fun nakshaSession() = NakshaSession(

    )
}