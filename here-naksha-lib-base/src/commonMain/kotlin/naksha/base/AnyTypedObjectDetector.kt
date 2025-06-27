@file:Suppress("OPT_IN_USAGE")

package naksha.base

import naksha.base.Platform.Platform_C.forKClass
import naksha.base.Platform.Platform_C.unbox
import naksha.base.PlatformMapApi.PlatformMapApi_C.map_get
import naksha.base.fn.Fn1
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
open class AnyTypedObjectDetector(
    /**
     * The base type to detect `"Feature"`.
     * @since 3.0
     */
    val baseFeatureType: PlatformType<out AnyTypedObject>?,

    /**
     * The base type to detect `"FeatureCollection"`.
     * @since 3.0
     */
    val baseCollectionType: PlatformType<out AnyTypedObject>?
) : TypeDetector {
    companion object TypedObjectDetector_C {
        /**
         * The [PlatformType] of [AnyTypedObjectDetector].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(AnyTypedObjectDetector::class).withPackageName(PACKAGE_NAME)

        /**
         * The default detector singleton.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        var defaultDetector: AnyTypedObjectDetector = AnyTypedObjectDetector(forKClass(AnyTypedObject::class), forKClass(AnyTypedObject::class))

        private fun isMomType(type: PlatformType<*>): Boolean = type.isMomType
        private val isMomTypeFn1: Fn1<Boolean, PlatformType<*>> = Fn1(::isMomType)
        private fun isDataHubType(type: PlatformType<*>): Boolean = type.isDataHubType
        private val isDataHubTypeFn1: Fn1<Boolean, PlatformType<*>> = Fn1(::isDataHubType)
        private fun isFeature(type: PlatformType<*>): Boolean = type.isFeature
        private val isFeatureFn1: Fn1<Boolean, PlatformType<*>> = Fn1(::isFeature)
        private fun isFeatureCollection(type: PlatformType<*>): Boolean = type.isFeatureCollection
        private val isFeatureCollectionFn1: Fn1<Boolean, PlatformType<*>> = Fn1(::isFeatureCollection)

        init { initialize() }
    }

    override fun detectMap(map: PlatformMap): PlatformType<out MapProxy<*, *>>? {
        val base_type: PlatformType<out AnyTypedObject>?
        val testFn1: Fn1<Boolean, PlatformType<*>>?
        val type_name = map_get(map, "type")
        if (type_name == "Feature") {
            base_type = baseFeatureType
            testFn1 = isFeatureFn1
        } else if (type_name == "FeatureCollection") {
            base_type = baseCollectionType
            testFn1 = isFeatureCollectionFn1
        } else {
            base_type = null
            testFn1 = null
        }
        if (base_type != null) {
            // momType
            val momType = map_get(map, "momType")
            if (momType is String) {
                val type = Platform.forFirstJsonType(momType, base_type, isMomTypeFn1)
                if (type != null) return type
            }

            // featureType
            val featureType = map_get(map, "featureType")
            if (featureType is String) {
                val type = Platform.forFirstJsonType(featureType, base_type, testFn1)
                if (type != null) return type
            }

            // properties.featureType
            val properties = unbox(map_get(map, "properties"))
            if (properties is PlatformMap) {
                val propertiesFeatureType = map_get(properties, "featureType")
                if (propertiesFeatureType is String) {
                    val type = Platform.forFirstJsonType(propertiesFeatureType, base_type, isDataHubTypeFn1)
                    if (type != null) return type
                }
            }
        }
        // Note: 'type' will be checked by Platform.detectMap, which calls this!
        return null
    }
}