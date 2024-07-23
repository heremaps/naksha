package naksha.model.request.condition

import naksha.geo.GeometryProxy
import kotlin.jvm.JvmField

/**
 * Tests for an intersection of features geometry with the given one.
 *
 * Examples:
 * ```Kotlin
 * Intersects(geoPoint, ST_Buffer(150000.0, geography = true))
 * ```
 * ```Java
 * new Intersects(geoPoint,
 *   new ST_Buffer(150000.0, true, null, null, null, null, null)
 * )
 * ```
 *
 * @property geometry the geometry against which existing features should be tested for intersection.
 * @property transformation the optional transformation to apply to the given geometry.
 */
class SpIntersects(
    @JvmField
    val geometry: GeometryProxy,
    @JvmField
    val transformation: SpTransformation? = null
) : SpatialOuery