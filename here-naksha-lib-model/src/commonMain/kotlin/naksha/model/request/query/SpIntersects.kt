@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import naksha.base.AnyObject
import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.geo.SpGeometry
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Tests for an intersection of features geometry with the given one.
 */
@JsExport
open class SpIntersects() : AnyObject(), ISpatialQuery {

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
     */
    @JsName("of")
    constructor(geometry: SpGeometry, transformation: SpTransformation) :this() {
        this.geometry = geometry
        this.transformation = transformation
    }

    companion object SpIntersectsCompanion {
        private val GEOMETRY = NotNullProperty<SpIntersects, SpGeometry>(SpGeometry::class)
        private val TRANSFORMATION = NullableProperty<SpIntersects, SpTransformation>(SpTransformation::class)
    }

    /**
     * The geometry against which existing features should be tested for intersection.
     */
    var geometry by GEOMETRY

    /**
     * The optional transformation to apply to the given geometry, before using it.
     */
    var transformation by TRANSFORMATION
}