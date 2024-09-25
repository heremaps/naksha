package naksha.model

import naksha.model.objects.NakshaFeature
import kotlin.test.Test

class PropertyFilterTest {

    @Test
    fun filter() {
        val mockStorage = mock<IStorage>
        val feature = NakshaFeature()
        feature.properties["foo"] = "bar"
        val tuple = Tuple()
    }

}