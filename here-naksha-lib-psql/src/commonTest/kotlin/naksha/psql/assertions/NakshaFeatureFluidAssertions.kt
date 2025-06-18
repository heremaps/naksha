package naksha.psql.assertions

import naksha.base.AnyList
import naksha.base.AnyObject
import naksha.geo.BBox
import naksha.geo.SpGeometry
import naksha.geo.SpPoint
import naksha.model.objects.NakshaFeature
import naksha.psql.assertions.NakshaPropertiesFluidAssertions.Companion.assertThatProperties
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NakshaFeatureFluidAssertions private constructor(val feature: NakshaFeature) {

    fun isIdenticalTo(
        other: NakshaFeature,
        ignoreProps: Boolean = true
    ): NakshaFeatureFluidAssertions {
        return hasId(other.id)
            .hasType(other.type)
            .hasBbox(other.bbox)
            .hasGeometry(other.geometry)
            .hasRefPoint(other.referencePoint)
            .apply {
                if (!ignoreProps) {
                    assertThatProperties(feature.properties)
                        .areIdenticalTo(other.properties)
                }
            }
    }

    fun hasId(id: String): NakshaFeatureFluidAssertions =
        apply { assertEquals(id, feature.id) }

    fun hasType(type: String): NakshaFeatureFluidAssertions =
        apply { assertEquals(type, feature.type) }

    fun hasBbox(boundingBox: BBox?): NakshaFeatureFluidAssertions =
        apply {
            val subjectBbox = feature.bbox
            if (boundingBox == null) {
                assertNull(subjectBbox)
            } else {
                assertNotNull(subjectBbox)
                CommonProxyAssertions.assertAnyListsEqual(
                    boundingBox.proxy(AnyList.TYPE),
                    subjectBbox.proxy(AnyList.TYPE),
                    "boundingBox"
                )
            }
        }

    fun hasGeometry(geometry: SpGeometry?): NakshaFeatureFluidAssertions =
        apply { assertGeometries(geometry, feature.geometry) }

    fun hasRefPoint(refPoint: SpPoint?): NakshaFeatureFluidAssertions =
        apply { assertGeometries(refPoint, feature.referencePoint) }


    fun hasPropertiesThat(propsAssertion: (NakshaPropertiesFluidAssertions) -> Unit): NakshaFeatureFluidAssertions =
        apply { propsAssertion(assertThatProperties(feature.properties)) }


    private fun assertGeometries(left: SpGeometry?, right: SpGeometry?) {
        if (left == null) {
            assertNull(right)
        } else {
            assertNotNull(right)
            CommonProxyAssertions.assertAnyObjectsEqual(
                left.proxy(AnyObject.TYPE),
                right.proxy(AnyObject.TYPE)
            )
        }
    }

    companion object {
        fun assertThatFeature(feature: NakshaFeature): NakshaFeatureFluidAssertions =
            NakshaFeatureFluidAssertions(feature)
    }
}