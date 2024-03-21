import com.here.naksha.lib.jbon.JbDictManager
import com.here.naksha.lib.jbon.JbSession
import com.here.naksha.lib.jbon.JvmEnv
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.TestMethodOrder

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
abstract class JbAbstractTest {
    val env = JvmEnv.get()
    val dictManager = JbDictManager()

    companion object {
        @BeforeAll
        @JvmStatic
        fun createSession() {
            val env = JvmEnv.get()
            JbSession.threadLocal.set(JbSession("test", env.randomString(), "testApp", "testAuthor"))
        }
    }
}