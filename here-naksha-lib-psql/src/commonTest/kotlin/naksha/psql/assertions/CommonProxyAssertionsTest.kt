package naksha.psql.assertions

import naksha.base.AnyObject
import naksha.model.XyzNs
import naksha.model.mom.MomDeltaNs
import naksha.model.objects.NakshaFeature
import naksha.model.objects.NakshaProperties
import kotlin.test.Test

class CommonProxyAssertionsTest {

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
        CommonProxyAssertions.assertAnyObjectsEqual(left, right)
    }

    @Test
    fun shouldIgnoreLogicallyEmptyValues(){
        // Given: Object with empty Xyz, without Delta
        val left = NakshaFeature().apply {
            id = "some"
            properties = NakshaProperties().apply {
                xyz = XyzNs()
                setRaw("featureType", "test_type")
            }
        }

        // And: Object with empty Delta, without Xyz
        val right = NakshaFeature().apply {
            id = "some"
            properties = NakshaProperties().apply {
                delta = MomDeltaNs()
                setRaw("featureType", "test_type")
            }
        }

        // Then:
        CommonProxyAssertions.assertAnyObjectsEqual(left, right)
    }
}