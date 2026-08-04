@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.ops

import naksha.base.NotNullProperty
import naksha.geo.SpGeometry
import naksha.model.objects.Member
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Tests if the geometry member at [at] intersects with the given [value] geometry.
 * @since 3.0
 */
@JsExport
class Intersects() : Op() {
    companion object Intersects_C {
        private val VALUE = NotNullProperty<Intersects, SpGeometry>(SpGeometry::class) { _,_ -> SpGeometry() }
        private val TRANSFORMERS = NotNullProperty<Intersects, SpTransformationList>(SpTransformationList::class) { _,_ -> SpTransformationList() }
    }

    @JsName("forName")
    constructor(at: String, geometry: SpGeometry, vararg transformers: SpTransformation) : this() {
        this.op = INTERSECTS
        this.at = at
        this.value = geometry
        val _transformers = this.transformers
        for (t in transformers) _transformers.add(t)
    }

    @JsName("forMember")
    constructor(at: Member, geometry: SpGeometry, vararg transformers: SpTransformation) : this(at.id, geometry, *transformers)

    /**
     * The geometry to test for intersection.
     * @since 3.0
     */
    var value: SpGeometry by VALUE

    /**
     * Optional transformations to apply to the geometry.
     * @since 3.0
     */
    var transformers: SpTransformationList by TRANSFORMERS
}
