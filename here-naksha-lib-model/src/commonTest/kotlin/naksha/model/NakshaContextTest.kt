package naksha.model

import naksha.base.Platform.Platform_C.forKClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class NakshaContextTest {
    class MyOwnContext: NakshaContext() {
        var myValue: Int = 5
    }

    @Test
    fun testOwnContext() {
        val test = NakshaContext.newInstance()
        val oldContext = NakshaContext.currentContext<NakshaContext>()
        val oldContextType = NakshaContext.contextType
        try {
            NakshaContext.contextType = forKClass(MyOwnContext::class)

            val context = assertIs<MyOwnContext>(NakshaContext.currentContext())
            assertEquals(5, context.myValue)
        } finally {
            NakshaContext.contextType = oldContextType
            oldContext.attachToCurrentThread()
        }
    }
}