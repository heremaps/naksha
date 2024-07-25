package naksha.model.request.condition

import naksha.geo.GeometryProxy
import naksha.model.request.condition.geometry.GeometryTransformation
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * Spatial operations always executed against the geometry.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
class SOp(
    val op: SOpType,
    val geometry: GeometryProxy,
    val geometryTransformation: GeometryTransformation?
) : Op {

    override fun getType(): OpType = op

    companion object SOpCompanion{
        /**
         * Returns an operation that tests for an intersection of features with the given geometry.
         *
         * @param geometry The geometry against which existing features should be tested for intersection.
         * @return The operation describing this.
         */
        @JvmStatic
        @JsStatic
        fun intersects(geometry: GeometryProxy): SOp {
            return SOp(
                SOpType.INTERSECTS,
                geometry,
                null
            )
        }

        /**
         * Transforms input geometry by adding transformation to it and creates operation that tests for an intersection of transformed feature.geometry.
         * Example:
         * <pre>
         * `SOp.intersects(geoPoint, bufferInMeters(150000.0))
         * SOp.intersects(geoPoint, bufferInRadius(0.04))`
         * </pre>
         *
         * @param geometry
         * @param geometryTransformation
         * @return
         */
        @JvmStatic
        @JsStatic
        fun intersectsWithTransformation(
            geometry: GeometryProxy,
            geometryTransformation: GeometryTransformation
        ): SOp {
            return SOp(
                SOpType.INTERSECTS,
                geometry,
                geometryTransformation
            )
        }
    }
}