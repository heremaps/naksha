@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.NotNullProperty
import naksha.base.AnyObject
import kotlin.js.JsExport

/**
 * Request options for [Request]'s.
 *
 * @property noFeature if _false_, the feature body will not be returned in the response, not as [ByteArray], nor as feature object.
 * @property geometry if _false_, the feature geometry will not be returned in the response, not as [ByteArray], nor as `feature.geometry` attribute.
 * @property refPoint if _false_, the feature reference point will not be returned in the response, not as [ByteArray], nor as `feature.referencePoint` attribute.
 * @property meta if _false_, the feature [metadata][naksha.model.Metadata] will not be returned in the response, this means the feature will not have an XYZ namespace (`feature.properties.@ns:com:here:xyz` attribute).
 * @property tags if _false_, the feature tags will not be returned in the response, not as [ByteArray], nor in the XYZ namespace (`feature.properties.@ns:com:here:xyz.tags` attribute).
 */
@JsExport
open class RowOptions : AnyObject() {

    companion object RowOptionsCompanion {
        private val BOOLEAN = NotNullProperty<RowOptions, Boolean>(Boolean::class) { _,_ -> true }
    }

    /**
     * If explicitly _false_, the feature body will not be returned in the response, not as [ByteArray], nor as feature object.
     */
    var feature by BOOLEAN
    /**
     * If explicitly _false_, the feature geometry will not be returned in the response, not as [ByteArray], nor as `feature.geometry` attribute.
     */
    var geometry by BOOLEAN
    /**
     * If explicitly _false_, the feature reference point will not be returned in the response, not as [ByteArray], nor as `feature.referencePoint` attribute.
     */
    var refPoint by BOOLEAN
    /**
     * the feature [metadata][naksha.model.Metadata] will not be returned in the response, this means the feature will not have an XYZ namespace (`feature.properties.@ns:com:here:xyz` attribute).
     */
    var meta by BOOLEAN
    /**
     * If explicitly _false_, the feature tags will not be returned in the response, not as [ByteArray], nor in the XYZ namespace (`feature.properties.@ns:com:here:xyz.tags` attribute).
     */
    var tags by BOOLEAN
}