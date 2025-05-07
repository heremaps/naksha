package naksha.model

import naksha.geo.SpPoint
import naksha.model.objects.NakshaFeature
import kotlin.test.Test
import kotlin.test.assertEquals

class MetadataTest {
    @Test
    fun shouldGenerateCorrectTileIds() {
        val feature = NakshaFeature("test")
        feature.geometry = SpPoint(0.0, 0.0)
        var tile_id = Metadata.calculateHereTile(feature)
        assertEquals(1476395008, tile_id)

        feature.geometry = SpPoint(14.6711371, 50.2906841)
        tile_id = Metadata.calculateHereTile(feature)
        assertEquals(1511238935, tile_id)
    }
}