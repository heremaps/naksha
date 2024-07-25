package naksha.model.request.condition.geometry

import kotlin.js.ExperimentalJsExport
import kotlin.js.ExperimentalJsStatic
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

@OptIn(ExperimentalJsExport::class)
@JsExport
class BufferTransformation private constructor(
    val distance: Double,
    private val properties: String?,
    childTransformation: GeometryTransformation? = null
) : GeometryTransformation(childTransformation) {
    fun getProperties(): String {
        if (properties == null) {
            return ""
        }
        return properties
    }

    @OptIn(ExperimentalJsStatic::class)
    companion object BufferTransformationCompanion {

        @JsStatic
        @JvmStatic
        fun bufferInRadius(distance: Double): GeometryTransformation {
            return BufferTransformation(distance, null)
        }

        @JsStatic
        @JvmStatic
        fun bufferInRadiusWithProperties(distance: Double, properties: String?): GeometryTransformation {
            return BufferTransformation(distance, properties)
        }

        @JsStatic
        @JvmStatic
        fun bufferInMeters(distance: Double): GeometryTransformation {
            return BufferTransformation(distance, null, GeographyTransformation())
        }

        @JsStatic
        @JvmStatic
        fun bufferInMetersWithProperties(distance: Double, properties: String?): GeometryTransformation {
            return BufferTransformation(distance, properties, GeographyTransformation())
        }
    }
}
