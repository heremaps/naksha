package naksha.model.request

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads

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

    companion object {
        fun bufferInRadius(distance: Double): GeometryTransformation {
            return BufferTransformation(distance, null)
        }

        fun bufferInRadiusWithProperties(distance: Double, properties: String?): GeometryTransformation {
            return BufferTransformation(distance, properties)
        }

        fun bufferInMeters(distance: Double): GeometryTransformation {
            return BufferTransformation(distance, null, GeographyTransformation())
        }

        fun bufferInMetersWithProperties(distance: Double, properties: String?): GeometryTransformation {
            return BufferTransformation(distance, properties, GeographyTransformation())
        }
    }
}
