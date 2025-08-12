@file:Suppress("OPT_IN_USAGE")

package naksha.base

import kotlin.js.JsExport

/**
 * The type detection interface, used as plugin for type detection by [Platform.box] or [Platform.fromJson].
 *
 * @since 3.0
 * @see Platform.box
 * @see Platform.fromJson
 * @see Platform.globalDetectors
 * @see FromJsonOptions.detectors
 */
@JsExport
interface TypeDetector {
    /**
     * Detects the [MapProxy] to the given [PlatformMap]. If the method returns `null`, the next detector is called, otherwise the returned [MapProxy] is accepted.
     * @param map The map to detect the [PlatformType] of.
     * @return the detected [MapProxy] or `null`, if this algorithm did not find any match.
     */
    fun detectMap(map: PlatformMap): PlatformType<out MapProxy<*, *>>?
}