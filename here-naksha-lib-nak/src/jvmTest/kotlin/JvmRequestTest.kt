import com.here.naksha.lib.base.Base
import com.here.naksha.lib.base.BaseList
import com.here.naksha.lib.base.Klass
import com.here.naksha.lib.base.NakReadFeatures
import com.here.naksha.lib.base.NakReadRequest
import com.here.naksha.lib.base.NakRequest
import com.here.naksha.lib.base.contains
import com.here.naksha.lib.base.get
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class JvmRequestTest {

    @Test
    fun shouldCreateValidReadRequest() {
        // given
        val request = NakReadFeatures()
        request.setQueryDeleted(true)
        request.setLimit(9)
        request.setNoMeta(true)
        request.setNoFeature(true)
        request.setNoGeometry(true)
        request.setNoTags(true)
        request.setCollectionIds(Klass.arrayKlass.newInstance("id1", "id2"))

        // when
        val o = Base.toJvmObject(request)!!

        // then
        assertEquals(request.isQueryDeleted(), o[NakReadFeatures.QUERY_DELETED])
        assertEquals(request.isNoMeta(), o[NakRequest.NO_META])
        assertEquals(request.isNoFeature(), o[NakRequest.NO_FEATURE])
        assertEquals(request.isNoTags(), o[NakRequest.NO_TAGS])
        assertEquals(request.isNoGeometry(), o[NakRequest.NO_GEOMETRY])
        assertEquals(request.getLimit(), o[NakReadRequest.LIMIT])
        assertEquals(request.getCollectionIds(), o[NakReadFeatures.COLLECTION_IDS])
        assertEquals("id1", request.getCollectionIds()[0])
        assertEquals("id2", request.getCollectionIds()[1])
    }

    @Test
    fun verifyDefaults() {
        // given
        val request = NakReadFeatures()

        // expect
        assertEquals(100_000, request.getLimit())
        assertFalse(request.isQueryDeleted())
        assertFalse(request.isNoFeature())
        assertFalse(request.isNoMeta())
        assertFalse(request.isNoTags())
        assertFalse(request.isNoTags())

        // but values should not exist in data
        val data = request.data()
        assertFalse(data.contains(NakReadFeatures.QUERY_DELETED))
        assertFalse(data.contains(NakRequest.NO_META))
        assertFalse(data.contains(NakRequest.NO_FEATURE))
        assertFalse(data.contains(NakRequest.NO_GEOMETRY))
        assertFalse(data.contains(NakReadRequest.LIMIT))
    }
}