@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import naksha.base.AnyObject
import naksha.base.NullableProperty
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * Base class for all transformation to be applied to client geometries.
 * @property childTransformation an optional child transformation that should be executed before this one.
 * @since 3.0
 * @see IQuery
 * @see ISpatialQuery
 * @see SpIntersects
 * @see SpTransformation
 * @see SpBuffer
 */
@JsExport
open class SpTransformation() : AnyObject() {

    @JsName("SpTransformationOf")
    constructor(childTransformation: SpTransformation) : this() {
        this.childTransformation = childTransformation
    }

    companion object SpTransformation_C {
        /**
         * The [PlatformType] of [SpTransformation].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(SpTransformation::class).withPackageName(PACKAGE_NAME)

        private val CHILD = NullableProperty<SpTransformation, SpTransformation>(TYPE)
    }

    /**
     * An optional child transformation that should be executed before this one.
     */
    var childTransformation by CHILD
}
