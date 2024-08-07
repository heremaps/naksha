@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import naksha.base.AnyObject
import naksha.base.NullableProperty
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Base class for all transformation to be applied to client geometries.
 * @property childTransformation an optional child transformation that should be executed before this one.
 */
@JsExport
open class SpTransformation() : AnyObject() {

    @JsName("of")
    constructor(childTransformation: SpTransformation) : this() {
        this.childTransformation = childTransformation
    }

    companion object SpTransformationCompanion {
        private val CHILD = NullableProperty<SpTransformation, SpTransformation>(SpTransformation::class)
    }

    /**
     * An optional child transformation that should be executed before this one.
     */
    var childTransformation by CHILD
}
