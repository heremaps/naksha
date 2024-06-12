package naksha.model.request

import naksha.model.Geometry
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Spatial operations always executed against the geometry.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
class SOp(
    val op: SOpType,
    val geometry: naksha.model.Geometry,
    val geometryTransformation: GeometryTransformation?
) : Op {

    override fun getType(): OpType = op

    companion object {
        /**
         * Returns an operation that tests for an intersection of features with the given geometry.
         *
         * @param geometry The geometry against which existing features should be tested for intersection.
         * @return The operation describing this.
         */
        fun intersects(geometry: naksha.model.Geometry): SOp {
            return return SOp(
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
        fun intersectsWithTransformation(
            geometry: Geometry,
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