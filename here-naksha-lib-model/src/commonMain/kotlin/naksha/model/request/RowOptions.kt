@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.NotNullProperty
import naksha.base.AnyObject
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Fine-grained control over which parts of [rows][naksha.model.Row] are needed in a result-set.
 */
@JsExport
open class RowOptions() : AnyObject() {

    /**
     * Create and initialize row options.
     * @param feature if features should be returned.
     * @param geometry if geometry should be returned.
     * @param refPoint if refPoint should be returned.
     * @param meta if metadata should be returned.
     * @param tags if tags should be returned.
     * @param attachment if the attachment should be returned.
     */
    @JsName("of")
    constructor(feature: Boolean, geometry: Boolean, refPoint: Boolean, meta: Boolean, tags: Boolean, attachment: Boolean) : this() {
        this.feature = feature
        this.geometry = geometry
        this.refPoint = refPoint
        this.meta = meta
        this.tags = tags
        this.attachment = attachment
    }

    companion object RowOptionsCompanion {
        /**
         * Create new row-options with all parts being enabled.
         * @return new row-options with all parts being enabled.
         */
        fun all(): RowOptions = RowOptions(feature = true, geometry = true, refPoint = true, meta = true, tags = true, attachment = true)

        /**
         * Create new row-options with all parts being disabled.
         * @return new row-options with all parts being disabled.
         */
        fun none(): RowOptions = RowOptions()

        private val BOOLEAN = NotNullProperty<RowOptions, Boolean>(Boolean::class) { _, _ -> false }
    }

    /**
     * If explicitly _true_, the feature body will be returned in the response, not as [ByteArray], nor as feature object.
     */
    var feature by BOOLEAN

    fun withFeature(enabled: Boolean): RowOptions {
        this.feature = enabled
        return this
    }

    /**
     * If explicitly _true_, the feature geometry will be returned in the response, not as [ByteArray], nor as `feature.geometry` attribute.
     */
    var geometry by BOOLEAN

    fun withGeometry(enabled: Boolean): RowOptions {
        this.geometry = enabled
        return this
    }

    /**
     * If explicitly _true_, the feature reference point will be returned in the response, not as [ByteArray], nor as `feature.referencePoint` attribute.
     */
    var refPoint by BOOLEAN

    fun withRefPoint(enabled: Boolean): RowOptions {
        this.refPoint = enabled
        return this
    }

    /**
     * If explicitly _true_, the feature [metadata][naksha.model.Metadata] will be returned in the response, this means the feature will not have an XYZ namespace (`feature.properties.@ns:com:here:xyz` attribute).
     */
    var meta by BOOLEAN

    fun withMeta(enabled: Boolean): RowOptions {
        this.meta = enabled
        return this
    }

    /**
     * If explicitly _true_, the feature tags will be returned in the response, not as [ByteArray], nor in the XYZ namespace (`feature.properties.@ns:com:here:xyz.tags` attribute).
     */
    var tags by BOOLEAN

    fun withTags(enabled: Boolean): RowOptions {
        this.tags = enabled
        return this
    }

    /**
     * If explicitly _true_, the attachment will be returned in the response.
     */
    var attachment by BOOLEAN

    fun withAttachment(enabled: Boolean): RowOptions {
        this.attachment = enabled
        return this
    }
}