import com.here.naksha.lib.base.symbol
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import com.here.naksha.lib.base.*
import com.here.naksha.lib.base.Base.Companion.initNak
import com.here.naksha.lib.base.Base.Companion.newObject
import kotlin.test.assertEquals

class JvmAuthTest {

    companion object {
        @BeforeAll
        @JvmStatic
        fun init() {
            initNak()
        }
    }

    @Test
    fun testAuth() {
    }

}