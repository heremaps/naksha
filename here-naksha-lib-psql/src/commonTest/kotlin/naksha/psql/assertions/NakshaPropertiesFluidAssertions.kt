package naksha.psql.assertions

import naksha.base.AnyList
import naksha.model.TagList
import naksha.model.XyzNs
import naksha.mom.v2.MomDeltaNs
import naksha.mom.v2.MomReferenceList
import naksha.model.objects.NakshaProperties
import naksha.mom.v2.MomProperties
import naksha.psql.assertions.AnyObjectFluidAssertions.Companion.assertThatAnyObject
import kotlin.test.assertEquals

class NakshaPropertiesFluidAssertions private constructor(
    val subject: NakshaProperties,
    private val momSubject: MomProperties = subject.proxy(MomProperties.TYPE)
) {

    fun areIdenticalTo(other: NakshaProperties): NakshaPropertiesFluidAssertions {
        val mom = other.proxy(MomProperties.TYPE)
        return hasXyzThat(other.xyz)
            .hasDelta(mom.delta)
            .hasReferences(mom.references)
            .hasFeatureType(other.featureType)
    }

    fun hasXyzThat(xyzNs: XyzNs): NakshaPropertiesFluidAssertions =
        apply { CommonProxyAssertions.assertAnyObjectsEqual(xyzNs, subject.xyz) }

    fun hasXyzThat(xyzAssertions: (AnyObjectFluidAssertions) -> Unit) = apply {
        xyzAssertions(assertThatAnyObject(subject.xyz))
    }

    fun hasDelta(delta: MomDeltaNs?): NakshaPropertiesFluidAssertions =
        apply { CommonProxyAssertions.assertAnyObjectsEqual(delta, momSubject.delta) }

    fun hasReferences(references: MomReferenceList?): NakshaPropertiesFluidAssertions =
        apply {
            CommonProxyAssertions.assertAnyListsEqual(
                references?.proxy(AnyList.TYPE),
                momSubject.references?.proxy(AnyList.TYPE)
            )
        }

    fun hasTags(tags: TagList?): NakshaPropertiesFluidAssertions =
        apply {
            CommonProxyAssertions.assertAnyListsEqual(
                tags?.proxy(AnyList.TYPE),
                subject.xyz.tags?.proxy(AnyList.TYPE)
            )
        }

    fun hasFeatureType(featureType: String?): NakshaPropertiesFluidAssertions =
        apply { assertEquals(featureType, subject.featureType) }

    companion object {
        fun assertThatProperties(subject: NakshaProperties) =
            NakshaPropertiesFluidAssertions(subject)
    }
}