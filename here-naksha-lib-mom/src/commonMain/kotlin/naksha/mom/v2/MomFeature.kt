@file:Suppress("OPT_IN_USAGE")

package naksha.mom.v2

import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import naksha.geo.FEATURE
import naksha.geo.GeoFeature
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * The default mom feature.
 * @since 3.0
 * @see MomProperties
 */
@JsExport
open class MomFeature : GeoFeature() {
    companion object MomFeature_C {
        /**
         * The [PlatformType] of [MomFeature_C].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(MomFeature::class)
            .withPackageName(PACKAGE_NAME)
            .withJsonType(FEATURE)
            .withIsMomType(true)
    }

    override val properties: MomProperties
        get() = getProperties(MomProperties.TYPE)
}