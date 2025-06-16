@file:Suppress("OPT_IN_USAGE")

package naksha.jbon

import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A dictionary entry.
 * @since 3.0.0
 */
@JsExport
data class DictEntry(
    /**
     * The dictionary to which the entry belongs.
     * @since 3.0.0
     */
    val dict: IDict,

    /**
     * The index of the entry in the [dictionary][IDict].
     * @since 3.0.0
     */
    val index: Int,

    /**
     * The entry value, must be one of:
     * - `null`
     * - `Boolean`
     * - `Int`
     * - `Int64`
     * - `Double`
     * - `String`
     * - `Map<String,Any?>` - with _Any_ again being limited to these types.
     * - `List<Any?>` - with _Any_ again being limited to these types.
     * @since 3.0.0
     */
    val value: Any?
) {
    companion object DictEntry_C {
        /**
         * The [PlatformType] of [DictEntry].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(DictEntry::class).withPackageName(PACKAGE_NAME)
    }
}