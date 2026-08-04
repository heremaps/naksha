package naksha.psql.assertions

import naksha.base.PAnyMap
import naksha.psql.assertions.CommonProxyAssertions.assertAnyObjectsEqual
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnyObjectFluidAssertions private constructor(val subject: PAnyMap) {

    fun isIdenticalTo(other: PAnyMap){
        assertAnyObjectsEqual(subject, other)
    }

    fun hasProperty(key: String, value: Any): AnyObjectFluidAssertions =
        apply {
            assertTrue(subject.contains(key), "Missing property: $key")
            assertEquals(value, subject[key])
        }

    fun isEmpty(): AnyObjectFluidAssertions =
        apply { subject.isEmpty() }

    companion object {
        fun assertThatAnyObject(subject: PAnyMap): AnyObjectFluidAssertions =
            AnyObjectFluidAssertions(subject)
    }
}