@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import naksha.base.*
import naksha.geo.SpGeometry
import naksha.base.NakshaError.NakshaError_C.ILLEGAL_STATE
import naksha.base.Platform.Platform_C.forKClass
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads

/**
 * Tests for an intersection of features geometry with the given one.
 * @since 3.0
 * @see IQuery
 * @see ISpatialQuery
 * @see SpIntersects
 * @see SpTransformation
 */
@JsExport
class SpIntersects() : AnyObject(), ISpatialQuery {

    /**
     * Create an initialized intersection.
     *
     * Examples:
     * ```Kotlin
     * SpIntersects(geoPoint, SpBuffer(150000.0, geography = true))
     * ```
     * ```Java
     * new SpIntersects(geoPoint,
     *   new SpBuffer(150000.0, true, null, null, null, null, null)
     * )
     * ```
     *
     * @property geometry the geometry against which existing features should be tested for intersection.
     * @property transformation the optional transformation to apply to the given geometry.
     * @since 3.0
     */
    @JsName("of")
    @JvmOverloads
    constructor(geometry: SpGeometry, transformation: SpTransformation? = null) :this() {
        this.geometry = geometry
        this.transformation = transformation
    }

    companion object SpIntersects_C {
        /**
         * The [PlatformType] of [SpIntersects].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(SpIntersects::class).withPackageName(PACKAGE_NAME)

        private val GEOMETRY = NotNullProperty<SpIntersects, SpGeometry>(SpGeometry.TYPE) {
          _,_ -> throw NakshaException(ILLEGAL_STATE, "geometry must not be null")
        }
        private val TRANSFORMATION_NULL = NullableProperty<SpIntersects, SpTransformation>(SpTransformation.TYPE)
    }

    /**
     * The geometry against which existing features should be tested for intersection.
     */
    var geometry by GEOMETRY

    /**
     * The optional transformation to apply to the given geometry, before using it.
     */
    var transformation by TRANSFORMATION_NULL

    // TODO: Refactor SpTransformation children into Tr{Name}, otherwise they are not distinct for spatial queries!
    //       This applies to SpBuffer -> TrBuffer
    //       Refactor transformation enumeration parameters into ETrSide, ETrEndCap, and ETrJoinStyle
}