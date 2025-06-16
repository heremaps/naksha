@file:Suppress("OPT_IN_USAGE", "MemberVisibilityCanBePrivate")

package naksha.model.request.query

import naksha.base.*
import naksha.base.Platform.Platform_C.forKClass
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads

/**
 * Computes a POLYGON or MULTIPOLYGON that represents all points whose distance from a geometry/geography is less than or equal to a given distance. A negative distance shrinks the geometry rather than expanding it. A negative distance may shrink a polygon completely, in which case POLYGON EMPTY is returned. For points and lines negative distances always return empty results.
 *
 * This is based upon the [PostGIS definition](https://postgis.net/docs/ST_Buffer.html).
 * @since 3.0
 * @see IQuery
 * @see ISpatialQuery
 * @see SpIntersects
 * @see SpTransformation
 * @see SpBuffer
 */
@JsExport
class SpBuffer() : SpTransformation() {
    /**
     * Create an initialized SP buffer.
     *
     * @param distance the distance to expand or shrink the client geometry.
     * @param geography for geography (_true_), the distance is specified in meters. For geometry (_false_), the distance is specified in the units of the Spatial Reference System of the geometry.
     * @param quad_segs number of line segments used to approximate a quarter circle (default is 8).
     * @param join join style (defaults to "round"). 'miter' is accepted as a synonym for 'mitre'.
     * @param endCap endcap style (defaults to "round"). 'butt' is accepted as a synonym for 'flat'.
     * @param side 'left' or 'right' performs a single-sided buffer on the geometry, with the buffered side relative to the direction of the line. This is only applicable to LINESTRING geometry and does not affect POINT or POLYGON geometries. By default, end caps are square.
     * @param childTransformation an optional child transformation to be executed before this transformation is applied.
     */
    @JsName("of")
    @JvmOverloads
    constructor(
        distance: Double,
        geography: Boolean = true,
        quadSegments: Int? = null,
        joinStyle: SpJoinStyle? = null,
        joinLimit: Double? = null,
        endCap: SpEndCap? = null,
        side: SpSide? = null,
        childTransformation: SpTransformation? = null
    ) : this() {
        this.distance = distance
        this.geography = geography
        this.quadSegments = quadSegments
        this.joinStyle = joinStyle
        this.joinLimit = joinLimit
        this.endCap = endCap
        this.side = side
        this.childTransformation = childTransformation
    }

    companion object SpBuffer_C {
        /**
         * The [PlatformType] of [SpBuffer].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(SpBuffer::class).withPackageName(PACKAGE_NAME)

        private val DOUBLE = NotNullProperty<SpBuffer, Double>(Double_TYPE) { _, _ -> 0.0 }
        private val DOUBLE_NULL = NullableProperty<SpBuffer, Double>(Double_TYPE)
        private val BOOLEAN = NotNullProperty<SpBuffer, Boolean>(Boolean_TYPE) { _, _ -> true }
        private val INT_NULL = NullableProperty<SpBuffer, Int>(Int_TYPE)
        private val JOIN_STYLE = NullableEnum<SpBuffer, SpJoinStyle>(SpJoinStyle.TYPE)
        private val ENDCAP_NULL = NullableEnum<SpBuffer, SpEndCap>(SpEndCap.TYPE)
        private val SIDE_NULL = NullableEnum<SpBuffer, SpSide>(SpSide.TYPE)
    }

    /**
     * The distance to expand or shrink the client geometry.
     * - For [geography] = `true`: The distance is specified in meters.
     * - For [geography] = `false`: The distance is specified in the units of the Spatial Reference System of the geometry.
     */
    var distance by DOUBLE

    /**
     *  If the buffer should be a geography (_true_, default), or a geometry (_false_), see [distance].
     */
    var geography by BOOLEAN

    /**
     * The number of line segments used to approximate a quarter circle, if _null_ the storage will decide.
     */
    var quadSegments by INT_NULL

    /**
     * The join style, if _null_, storage selects the default.
     */
    var joinStyle by JOIN_STYLE

    /**
     * The optional mitre ratio limit.
     */
    var joinLimit by DOUBLE_NULL

    /**
     * End-cap style, if _null_, storage selects the default.
     */
    var endCap by ENDCAP_NULL

    /**
     * The side, if _null_, storage selects the default.
     *
     * 'left' or 'right' performs a single-sided buffer on the geometry, with the buffered side relative to the direction of the line. This is only applicable to LINESTRING geometry and does not affect POINT or POLYGON geometries. By default, end caps are square.
     */
    var side by SIDE_NULL
}