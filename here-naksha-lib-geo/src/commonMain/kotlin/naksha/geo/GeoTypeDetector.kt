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
 * This detector automatically replaces the [AnyTypedObjectDetector.defaultDetector], but this can be reverted via:
 * ```kotlin
 * Platform.globalDetectors.remove(
 *   GeoTypeDetector.defaultGeoDetector
 * )
 * Platform.globalDetectors.add(
 *   AnyTypedObjectDetector.defaultDetector
 * )
 * ```
 *
 * @since 3.0
 * @see GeoFeature
 */
@JsExport
class GeoTypeDetector: AnyTypedObjectDetector(forKClass(GeoFeature::class), forKClass(GeoCollection::class)) {

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
        val defaultGeoDetector: GeoTypeDetector = GeoTypeDetector()

        init { initialize() }
    }
}