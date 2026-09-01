@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.ops

import naksha.base.NotNullProperty
import naksha.base.NullableEnum
import naksha.base.NullableProperty
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.jvm.JvmOverloads

/**
 * Computes a POLYGON or MULTIPOLYGON that represents all points whose distance from a geometry/geography
 * is less than or equal to a given distance.
 */
@JsExport
open class SpBuffer() : SpTransformation() {
    @JsName("of")
    @JvmOverloads
    constructor(
        distance: Double,
        geography: Boolean = true,
        quadSegments: Int? = null,
        joinStyle: SpJoinStyle? = null,
        joinLimit: Double? = null,
        endCap: SpEndCap? = null,
        side: SpSide? = null
    ) : this() {
        this.distance = distance
        this.geography = geography
        this.quadSegments = quadSegments
        this.joinStyle = joinStyle
        this.joinLimit = joinLimit
        this.endCap = endCap
        this.side = side
    }

    companion object SpBuffer_C {
        private val DOUBLE = NotNullProperty<SpBuffer, Double>(Double::class) { _,_ -> 0.0 }
        private val DOUBLE_NULL = NullableProperty<SpBuffer, Double>(Double::class)
        private val BOOLEAN = NotNullProperty<SpBuffer, Boolean>(Boolean::class) { _,_ -> true }
        private val INT_NULL = NullableProperty<SpBuffer, Int>(Int::class)
        private val JOIN_STYLE = NullableEnum<SpBuffer, SpJoinStyle>(SpJoinStyle::class)
        private val ENDCAP_NULL = NullableEnum<SpBuffer, SpEndCap>(SpEndCap::class)
        private val SIDE_NULL = NullableEnum<SpBuffer, SpSide>(SpSide::class)
    }

    var distance by DOUBLE
    var geography by BOOLEAN
    var quadSegments by INT_NULL
    var joinStyle by JOIN_STYLE
    var joinLimit by DOUBLE_NULL
    var endCap by ENDCAP_NULL
    var side by SIDE_NULL
}
