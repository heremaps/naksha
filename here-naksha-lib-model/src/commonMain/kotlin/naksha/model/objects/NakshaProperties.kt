@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.*
import naksha.base.Platform.Platform_C.forKClass
import naksha.model.XyzNs
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * The properties of a standard Naksha feature.
 * @since 1.0
 */
@JsExport
open class NakshaProperties : AnyTypedObject() {

    companion object NakshaProperties_C {
        /**
         * The [PlatformType] of [NakshaProperties].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(NakshaProperties::class).withPackageName(PACKAGE_NAME)

        /**
         * The key of the feature-type property (`featureType`).
         * @since 3.0
         */
        const val FEATURE_TYPE = "featureType"

        /**
         * The key of the XYZ namespace property (`@ns:com:here:xyz`).
         * @since 3.0
         */
        const val XYZ_KEY = "@ns:com:here:xyz"

        /**
         * The key of the tags, they do override the tags in the XYZ namespace when set.
         * @since 3.0
         */
        const val TAGS = "tags"

        private val _XYZ = NotNullProperty<NakshaProperties, XyzNs>(XyzNs.TYPE, name = XYZ_KEY)
    }

    /**
     * The XYZ namespace, a must in all Naksha features (`@ns:com:here:xyz`).
     * @since 1.0
     */
    var xyz by _XYZ
}