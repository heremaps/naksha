package naksha.psql.assertions

import naksha.base.AnyList
import naksha.base.AnyObject
import naksha.geo.SpBoundingBox
import naksha.geo.SpGeometry
import naksha.geo.SpPoint
import naksha.model.objects.NakshaFeature
import naksha.psql.assertions.NakshaPropertiesFluidAssertions.Companion.assertThatProperties
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NakshaFeatureFluidAssertions private constructor(val subject: NakshaFeature) {

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
                    assertThatProperties(subject.properties)
                        .areIdenticalTo(other.properties)
                }
            }
    }

    fun hasId(id: String): NakshaFeatureFluidAssertions =
        apply { assertEquals(id, subject.id) }

    fun hasType(type: String): NakshaFeatureFluidAssertions =
        apply { assertEquals(type, subject.type) }

    fun hasBbox(boundingBox: SpBoundingBox?): NakshaFeatureFluidAssertions =
        apply {
            val subjectBbox = subject.bbox
            if (boundingBox == null) {
                assertNull(subjectBbox)
            } else {
                assertNotNull(subjectBbox)
                CommonProxyAssertions.assertAnyListsEqual(
                    boundingBox.proxy(AnyList::class),
                    subjectBbox.proxy(AnyList::class),
                    "boundingBox"
                )
            }
        }

    fun hasGeometry(geometry: SpGeometry?): NakshaFeatureFluidAssertions =
        apply { assertGeometries(geometry, subject.geometry) }

    fun hasRefPoint(refPoint: SpPoint?): NakshaFeatureFluidAssertions =
        apply { assertGeometries(refPoint, subject.referencePoint) }


    fun hasPropertiesThat(propsAssertion: (NakshaPropertiesFluidAssertions) -> Unit): NakshaFeatureFluidAssertions =
        apply { propsAssertion(assertThatProperties(subject.properties)) }


    private fun assertGeometries(left: SpGeometry?, right: SpGeometry?) {
        if (left == null) {
            assertNull(right)
        } else {
            assertNotNull(right)
            CommonProxyAssertions.assertAnyObjectsEqual(
                left.proxy(AnyObject::class),
                right.proxy(AnyObject::class)
            )
        }
    }

    companion object {
        fun assertThatFeature(feature: NakshaFeature): NakshaFeatureFluidAssertions =
            NakshaFeatureFluidAssertions(feature)
    }
}