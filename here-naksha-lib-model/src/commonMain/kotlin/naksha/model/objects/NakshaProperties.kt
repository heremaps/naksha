@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.AnyObject
import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.model.XyzNs
import naksha.model.mom.MomDeltaNs
import naksha.model.mom.MomReferenceList
import kotlin.js.JsExport

/**
 * The properties of a standard Naksha feature.
 */
@JsExport
open class NakshaProperties : AnyObject() {
    companion object {
        const val FEATURE_TYPE = "featureType"
        const val XYZ_KEY = "@ns:com:here:xyz"
        const val DELTA_KEY = "@ns:com:here:delta"
        const val META_KEY = "@ns:com:here:meta"
        /**
         * Properties used by the deprecated Activity-Log service, just here to allow downward
         * compatibility.
         */
        const val XYZ_ACTIVITY_LOG_NS = "@ns:com:here:xyz:log"

        private val XYZ = NotNullProperty<NakshaProperties, XyzNs>(XyzNs::class, name = XYZ_KEY)
        private val DELTA_PROXY_NULL = NullableProperty<NakshaProperties, MomDeltaNs>(MomDeltaNs::class, name = DELTA_KEY)
        private val REFERENCES = NullableProperty<NakshaProperties, MomReferenceList>(MomReferenceList::class)
        private val STRING_NULL = NullableProperty<NakshaProperties, String>(String::class)
    }

    /**
     * The XYZ namespace, a must in all Naksha features (`@ns:com:here:xyz`).
     */
    var xyz by XYZ

    /**
     * The MOM delta namespace.
     */
    var delta by DELTA_PROXY_NULL

    /**
     * References to MOM objects.
     */
    var references: MomReferenceList? by REFERENCES

    /**
     * The feature-type; if any.
     */
    var featureType by STRING_NULL

    fun useDeltaNamespace(): MomDeltaNs {
        var deltaProxy = this.delta
        if (deltaProxy == null) {
            deltaProxy = MomDeltaNs()
            this.delta = deltaProxy
        }
        return deltaProxy
    }
}