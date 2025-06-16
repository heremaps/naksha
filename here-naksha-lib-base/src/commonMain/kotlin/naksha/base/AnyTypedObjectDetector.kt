@file:Suppress("OPT_IN_USAGE")

package naksha.base

import naksha.base.Platform.Platform_C.forKClass
import naksha.base.Platform.Platform_C.unbox
import naksha.base.PlatformMapApi.PlatformMapApi_C.map_get
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * This detector implements the special type detection for [HERE Technologies](https://here.com), see [AnyTypedObject].
 *
 * # Note
 * This detector is automatically added, but can be removed:
 * ```kotlin
 * Platform.globalDetectors.remove(
 *   TypedObjectDetector.instance
 * )
 * ```
 *
 * @since 3.0
 * @see AnyTypedObject
 */
@JsExport
class AnyTypedObjectDetector private constructor(): TypeDetector {

    companion object TypedObjectDetector_C {
        /**
         * The [PlatformType] of [AnyTypedObjectDetector].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(AnyTypedObjectDetector::class).withPackageName(PACKAGE_NAME)

        /**
         * The [AnyTypedObjectDetector] singleton.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val instance: AnyTypedObjectDetector = AnyTypedObjectDetector()

        init { initialize() }
    }

    override fun detectMap(map: PlatformMap): PlatformType<out MapProxy<*, *>>? {
        val type_name = map_get(map, "type")
        if (type_name == "Feature" || type_name == "FeatureCollection") {
            // momType
            val momType = map_get(map, "momType")
            if (momType is String) {
                val type = Platform.forFirstJsonType(momType, AnyTypedObject.TYPE)
                if (type != null) return type
            }

            // featureType
            val featureType = map_get(map, "featureType")
            if (featureType is String) {
                val type = Platform.forFirstJsonType(featureType, AnyTypedObject.TYPE)
                if (type != null) return type
            }

            // properties.featureType
            val properties = unbox(map_get(map, "properties"))
            if (properties is PlatformMap) {
                val propertiesFeatureType = map_get(properties, "featureType")
                if (propertiesFeatureType is String) {
                    val type = Platform.forFirstJsonType(propertiesFeatureType, AnyTypedObject.TYPE)
                    if (type != null) return type
                }
            }
        }
        return null
    }
}