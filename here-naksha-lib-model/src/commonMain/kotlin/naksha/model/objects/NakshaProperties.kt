@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.AnyObject
import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.model.XyzNs
import naksha.model.mom.MomDeltaNs
import naksha.model.mom.MomMetaNs
import naksha.model.mom.MomReferenceList
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * The properties of a standard Naksha feature.
 * @since 3.0.0
 */
@JsExport
open class NakshaProperties() : AnyObject() {

    /**
     * Create new properties with the given type set.
     * @param featureType the [featureType] to set.
     * @since 3.0.0
     */
    @JsName("ofType")
    constructor(featureType: String): this() {
        this.featureType = featureType
    }

    companion object {
        /**
         * The key of the feature-type property (`featureType`).
         * @since 3.0.0
         */
        const val FEATURE_TYPE = "featureType"

        /**
         * The key of the XYZ namespace property (`@ns:com:here:xyz`).
         * @since 3.0.0
         */
        const val XYZ_KEY = "@ns:com:here:xyz"

        /**
         * The key of the Mom-Delta namespace property (`@ns:com:here:delta`).
         * @since 3.0.0
         */
        const val DELTA_KEY = "@ns:com:here:delta"

        /**
         * The key of the Mom-Meta namespace property (`@ns:com:here:meta`).
         * @since 3.0.0
         */
        const val META_KEY = "@ns:com:here:meta"

        /**
         * Properties used by the deprecated Activity-Log service, just here to allow downward
         * compatibility.
         */
        const val XYZ_ACTIVITY_LOG_NS = "@ns:com:here:xyz:log"

        /**
         * The key of the tags, they do override the tags in the XYZ namespace when set.
         * @since 3.0.0
         */
        const val TAGS = "tags"

        private val _XYZ = NotNullProperty<NakshaProperties, XyzNs>(XyzNs::class, name = XYZ_KEY)
        private val _DELTA_PROXY_NULL = NullableProperty<NakshaProperties, MomDeltaNs>(MomDeltaNs::class, name = DELTA_KEY, autoRemove = true)
        private val _META_PROXY_NULL = NullableProperty<NakshaProperties, MomMetaNs>(MomMetaNs::class, name = META_KEY, autoRemove = true)
        private val _REFERENCES_NULL = NullableProperty<NakshaProperties, MomReferenceList>(MomReferenceList::class, autoRemove = true)
        private val _STRING_NULL = NullableProperty<NakshaProperties, String>(String::class, autoRemove = true)
    }

    /**
     * The XYZ namespace, a must in all Naksha features (`@ns:com:here:xyz`).
     * @since 1.0.0
     */
    var xyz by _XYZ

    /**
     * The MOM delta namespace.
     * @since 1.0.0
     */
    var delta by _DELTA_PROXY_NULL

    /**
     * The MOM delta namespace.
     * @since 1.0.0
     */
    var meta by _META_PROXY_NULL

    /**
     * References to MOM objects.
     * @since 1.0.0
     */
    var references by _REFERENCES_NULL

    /**
     * The feature-type; if any is set.
     *
     * **This property should not be read directly, please use [NakshaFeature.featureType] or [NakshaFeature.momType] instead!**
     * @since 1.0.0
     */
    var featureType by _STRING_NULL

    fun useDeltaNamespace(): MomDeltaNs {
        var deltaProxy = this.delta
        if (deltaProxy == null) {
            deltaProxy = MomDeltaNs()
            this.delta = deltaProxy
        }
        return deltaProxy
    }
}