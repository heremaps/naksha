package naksha.psql.assertions

import naksha.base.PAnyArray
import naksha.model.TagList
import naksha.model.XyzNs
import naksha.model.mom.MomDeltaNs
import naksha.model.mom.MomReferenceList
import naksha.model.objects.NakshaProperties
import naksha.psql.assertions.AnyObjectFluidAssertions.Companion.assertThatAnyObject
import kotlin.test.assertEquals

class NakshaPropertiesFluidAssertions private constructor(val subject: NakshaProperties) {

    fun areIdenticalTo(other: NakshaProperties): NakshaPropertiesFluidAssertions {
        return hasXyzThat(other.xyz)
            .hasDelta(other.delta)
            .hasReferences(other.references)
            .hasFeatureType(other.featureType)
    }

    fun hasXyzThat(xyzNs: XyzNs): NakshaPropertiesFluidAssertions =
        apply { CommonProxyAssertions.assertAnyObjectsEqual(xyzNs, subject.xyz) }

    fun hasXyzThat(xyzAssertions: (AnyObjectFluidAssertions) -> Unit) = apply {
        xyzAssertions(assertThatAnyObject(subject.xyz))
    }

    fun hasDelta(delta: MomDeltaNs?): NakshaPropertiesFluidAssertions =
        apply { CommonProxyAssertions.assertAnyObjectsEqual(delta, subject.delta) }

    fun hasReferences(references: MomReferenceList?): NakshaPropertiesFluidAssertions =
        apply {
            CommonProxyAssertions.assertAnyListsEqual(
                references?.proxy(PAnyArray::class),
                subject.references?.proxy(PAnyArray::class)
            )
        }

    fun hasTags(tags: TagList?): NakshaPropertiesFluidAssertions =
        apply {
            CommonProxyAssertions.assertAnyListsEqual(
                tags?.proxy(PAnyArray::class),
                subject.xyz.tags?.proxy(PAnyArray::class)
            )
        }

    fun hasFeatureType(featureType: String?): NakshaPropertiesFluidAssertions =
        apply { assertEquals(featureType, subject.featureType) }

    companion object {
        fun assertThatProperties(subject: NakshaProperties) =
            NakshaPropertiesFluidAssertions(subject)
    }
}