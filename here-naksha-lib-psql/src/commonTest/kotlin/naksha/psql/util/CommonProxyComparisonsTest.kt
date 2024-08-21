package naksha.psql.util

import naksha.base.AnyObject
import naksha.model.XyzNs
import naksha.model.mom.MomDeltaNs
import naksha.model.objects.NakshaFeature
import naksha.model.objects.NakshaProperties
import kotlin.test.Test

class CommonProxyComparisonsTest {

    @Test
    fun shouldTreatSameObjectsAsEqual(){
        // Given:
        val left = NakshaFeature().apply {
            id = "some"
            properties = NakshaProperties().apply {
                xyz = AnyObject().apply {
                    setRaw("appId", "someAppId")
                }.proxy(XyzNs::class)
            }
        }

        // And:
        val right = left.copy<NakshaFeature>(true)

        // Then
        CommonProxyComparisons.assertAnyObjectsEqual(left, right)
    }

    @Test
    fun shouldIgnoreLogicallyEmptyValues(){
        // Given: Object with empty Xyz, without Delta
        val left = NakshaFeature().apply {
            id = "some"
            properties = NakshaProperties().apply {
                xyz = XyzNs()
                featureType = "test_type"
            }
        }

        // And: Object with empty Delta, without Xyz
        val right = NakshaFeature().apply {
            id = "some"
            properties = NakshaProperties().apply {
                delta = MomDeltaNs()
                featureType = "test_type"
            }
        }

        // Then:
        CommonProxyComparisons.assertAnyObjectsEqual(left, right)
    }
}