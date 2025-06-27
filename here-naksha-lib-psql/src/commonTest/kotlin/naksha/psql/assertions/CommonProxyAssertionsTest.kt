package naksha.psql.assertions

import naksha.model.XyzNs
import naksha.mom.v2.MomDeltaNs
import naksha.model.objects.NakshaFeature
import naksha.mom.v2.MomFeature
import kotlin.test.Test

class CommonProxyAssertionsTest {

    @Test
    fun shouldTreatSameObjectsAsEqual() {
        // Given:
        val left = NakshaFeature().apply {
            id = "some"
            properties.apply {
                xyz = XyzNs().apply {
                    set("appId", "someAppId")
                }
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
            properties.apply {
                xyz = XyzNs()
                set("featureType", "test_type")
            }
        }

        // And: Object with empty Delta, without Xyz
        val right = MomFeature().apply {
            id = "some"
            properties.apply {
                delta = MomDeltaNs()
                set("featureType", "test_type")
            }
        }

        // Then:
        CommonProxyAssertions.assertAnyObjectsEqual(left, right)
    }
}