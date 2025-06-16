@file:Suppress("OPT_IN_USAGE")

package naksha.geo

import naksha.base.*
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.Platform.Platform_C.unbox
import naksha.base.PlatformMapApi.PlatformMapApi_C.map_get
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * This detector implements the special type detection for [HERE Technologies](https://here.com), see [GeoFeature].
 *
 * # Note
 * This detector is automatically added, but can be removed:
 * ```kotlin
 * Platform.globalDetectors.add(GeoTypeDetector.instance)
 * ```
 *
 * @since 3.0
 * @see GeoFeature
 */
@JsExport
class GeoTypeDetector private constructor(): TypeDetector {

    companion object GeoTypeDetector_C {
        /**
         * The [PlatformType] of [GeoTypeDetector].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(GeoTypeDetector::class).withPackageName(PACKAGE_NAME)

        /**
         * The [GeoTypeDetector] singleton.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val instance: GeoTypeDetector = GeoTypeDetector()

        init {
            initialize()
        }
    }

    override fun detectMap(map: PlatformMap): PlatformType<out MapProxy<*, *>>? {
        val type_name = map_get(map, "type")
        val GEO_TYPE: PlatformType<out MapProxy<*, *>>? =
            if (type_name == "FeatureCollection") GeoCollection.TYPE
            else if (type_name == "Feature") GeoFeature.TYPE
            else null
        if (GEO_TYPE != null) {
            // momType
            val momType = map_get(map, "momType")
            if (momType is String) {
                val type = Platform.forFirstJsonType(momType, GEO_TYPE)
                if (type != null) return type
            }

            // featureType
            val featureType = map_get(map, "featureType")
            if (featureType is String) {
                val type = Platform.forFirstJsonType(featureType, GEO_TYPE)
                if (type != null) return type
            }

            // properties.featureType
            val properties = unbox(map_get(map, "properties"))
            if (properties is PlatformMap) {
                val propertiesFeatureType = map_get(properties, "featureType")
                if (propertiesFeatureType is String) {
                    val type = Platform.forFirstJsonType(propertiesFeatureType, GEO_TYPE)
                    if (type != null) return type
                }
            }
        }
        return null
    }
}