@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * Request options for [Request]'s.
 *
 * @property feature if _false_, the feature body will not be returned in the response, not as [ByteArray], nor as feature object.
 * @property geometry if _false_, the feature geometry will not be returned in the response, not as [ByteArray], nor as `feature.geometry` attribute.
 * @property refPoint if _false_, the feature reference point will not be returned in the response, not as [ByteArray], nor as `feature.referencePoint` attribute.
 * @property meta if _false_, the feature [metadata][naksha.model.Metadata] will not be returned in the response, this means the feature will not have an XYZ namespace (`feature.properties.@ns:com:here:xyz` attribute).
 * @property tags if _false_, the feature tags will not be returned in the response, not as [ByteArray], nor in the XYZ namespace (`feature.properties.@ns:com:here:xyz.tags` attribute).
 */
@JsExport
data class RowOptions(
    @JvmField
    val feature: Boolean = true,
    @JvmField
    val geometry: Boolean = true,
    @JvmField
    val refPoint: Boolean = true,
    @JvmField
    val meta: Boolean = true,
    @JvmField
    val tags: Boolean = true
) {
    fun withNoFeature(): RowOptions = if (feature) copy(feature=false) else this
    fun withFeatures(): RowOptions = if (!feature) copy(feature=true) else this
    fun withNoGeometry(): RowOptions = if (geometry) copy(geometry=false) else this
    fun withGeometry(): RowOptions = if (!geometry) copy(geometry=true) else this
    fun withNoRefPoint(): RowOptions = if (refPoint) copy(refPoint=false) else this
    fun withRefPoint(): RowOptions = if (!refPoint) copy(refPoint=true) else this
    fun withNoMeta(): RowOptions = if (meta) copy(meta=false) else this
    fun withMeta(): RowOptions = if (!meta) copy(meta=true) else this
    fun withNoTags(): RowOptions = if (tags) copy(tags=false) else this
    fun withTags(): RowOptions = if (!tags) copy(tags=true) else this
}