package naksha.psql.assertions

import naksha.base.AnyObject
import naksha.model.XyzNs
import naksha.model.mom.MomMetaNs
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
        // Given: Object with empty Xyz, without Meta
        val left = NakshaFeature().apply {
            id = "some"
            properties = NakshaProperties().apply {
                xyz = XyzNs()
                setRaw("featureType", "test_type")
            }
        }

        // And: Object with empty Meta, without Xyz
        val right = NakshaFeature().apply {
            id = "some"
            properties = NakshaProperties().apply {
                meta = MomMetaNs()
                setRaw("featureType", "test_type")
            }
        }

        // Then:
        CommonProxyAssertions.assertAnyObjectsEqual(left, right)
    }
}