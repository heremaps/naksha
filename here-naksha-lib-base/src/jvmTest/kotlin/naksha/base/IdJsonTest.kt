package naksha.base

import kotlin.test.Test
import kotlin.test.assertEquals

class IdJsonTest {
    @Test
    fun idIsWrittenAsText() {
        val map = AnyObject()
        map["text"] = Id("abc")
        map["number"] = Id(42L)
        assertEquals("""{"text":"abc","number":"42"}""", Platform.toJSON(map))
    }

    @Test
    fun cachedIdRoundTrips() {
        val map = AnyObject()
        map["id"] = "abc"
        map.getId("id", update = true)
        val back = Platform.fromJSON(Platform.toJSON(map)) as PlatformMap
        assertEquals("abc", back.proxy(AnyObject::class)["id"])
    }
}
