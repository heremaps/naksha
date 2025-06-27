@file:Suppress("OPT_IN_USAGE")

package naksha.mom.v2

import naksha.base.AnyObject
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import naksha.geo.BBox
import naksha.geo.FEATURE
import naksha.geo.GeoCollection.GeoCollection_C.FEATURES
import naksha.geo.GeoFeature
import naksha.geo.SpGeometry
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

    override fun withType(type: String?): MomFeature = super.withType(type) as MomFeature
    override fun withId(id: String): MomFeature = super.withId(id) as MomFeature
    override fun withBBox(bbox: BBox): MomFeature = super.withBBox(bbox) as MomFeature
    override fun withAutoBBox(): MomFeature = super.withAutoBBox() as MomFeature
    override fun withGeometry(geometry: SpGeometry?): MomFeature = super.withGeometry(geometry) as MomFeature
    override val properties: MomProperties
        get() = get_properties(MomProperties.TYPE)
    override fun withProperties(properties: AnyObject): MomFeature = super.withProperties(properties) as MomFeature
}