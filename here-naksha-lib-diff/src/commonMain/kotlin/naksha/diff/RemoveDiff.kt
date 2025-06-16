@file:Suppress("OPT_IN_USAGE")

package naksha.diff

import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A difference where an existing [oldValue] is removed.
 * @since 3.0
 */
@JsExport
class RemoveDiff() : PrimitiveDiff() {

    @JsName("of")
    constructor(oldValue: Any?): this() {
        setRaw(OLD_VALUE_KEY, oldValue)
    }

    companion object RemoveDiff_C {
        /**
         * The [PlatformType] of [RemoveDiff].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(RemoveDiff::class).withPackageName(PACKAGE_NAME).withNameAsJsonType()
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is RemoveDiff && oldValue == other.oldValue)

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + (oldValue?.hashCode() ?: 0)
        return result
    }
}