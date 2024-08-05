@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.NotNullProperty
import naksha.base.AnyObject
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * Fine-grained control over which parts of [rows][naksha.model.Tuple] are needed in a result-set.
 */
@JsExport
open class ReturnColumns() : AnyObject() {

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

    companion object ReturnColumns_C {
        /**
         * Create return-options with all columns being enabled.
         * @return new return-options with all columns being enabled.
         */
        @JvmStatic
        @JsStatic
        fun all(): ReturnColumns = ReturnColumns(feature = true, geometry = true, refPoint = true, meta = true, tags = true, attachment = true)

        /**
         * Create new return-options with all columns being disabled (only returns [naksha.model.TupleNumber]).
         * @return new return-options with all columns being disabled (only returns [naksha.model.TupleNumber]).
         */
        @JvmStatic
        @JsStatic
        fun none(): ReturnColumns = ReturnColumns()

        private val BOOLEAN = NotNullProperty<ReturnColumns, Boolean>(Boolean::class) { _, _ -> false }
    }

    /**
     * If explicitly _true_, the feature body will be returned in the response, not as [ByteArray], nor as feature object.
     */
    var feature by BOOLEAN

    fun withFeature(enabled: Boolean): ReturnColumns {
        this.feature = enabled
        return this
    }

    /**
     * If explicitly _true_, the feature geometry will be returned in the response, not as [ByteArray], nor as `feature.geometry` attribute.
     */
    var geometry by BOOLEAN

    fun withGeometry(enabled: Boolean): ReturnColumns {
        this.geometry = enabled
        return this
    }

    /**
     * If explicitly _true_, the feature reference point will be returned in the response, not as [ByteArray], nor as `feature.referencePoint` attribute.
     */
    var refPoint by BOOLEAN

    fun withRefPoint(enabled: Boolean): ReturnColumns {
        this.refPoint = enabled
        return this
    }

    /**
     * If explicitly _true_, the feature [metadata][naksha.model.Metadata] will be returned in the response, this means the feature will not have an XYZ namespace (`feature.properties.@ns:com:here:xyz` attribute).
     */
    var meta by BOOLEAN

    fun withMeta(enabled: Boolean): ReturnColumns {
        this.meta = enabled
        return this
    }

    /**
     * If explicitly _true_, the feature tags will be returned in the response, not as [ByteArray], nor in the XYZ namespace (`feature.properties.@ns:com:here:xyz.tags` attribute).
     */
    var tags by BOOLEAN

    fun withTags(enabled: Boolean): ReturnColumns {
        this.tags = enabled
        return this
    }

    /**
     * If explicitly _true_, the attachment will be returned in the response.
     */
    var attachment by BOOLEAN

    fun withAttachment(enabled: Boolean): ReturnColumns {
        this.attachment = enabled
        return this
    }
}