@file:Suppress("OPT_IN_USAGE")

package naksha.diff

import naksha.base.AnyObject
import naksha.base.NotNullProperty
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A difference between two maps.
 * @since 3.0
 */
@JsExport
class MapDiff : AnyObject(), Difference {
    companion object MapDiff_C {
        /**
         * The [PlatformType] of [MapDiff].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(MapDiff::class).withPackageName(PACKAGE_NAME).withNameAsJsonType()

        private val DIFFERENCES = NotNullProperty<MapDiff, DifferenceMap>(DifferenceMap.TYPE) { _, _ -> DifferenceMap() }
    }

    /**
     * The differences, a map that describes the differences, for each key where something changes a [Difference] is stored as value in this [DifferenceMap], unchanged keys are `undefined` in this map.
     * @since 3.0
     */
    val differences: DifferenceMap by DIFFERENCES
}