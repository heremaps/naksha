@file:Suppress("OPT_IN_USAGE")

package naksha.diff

import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A difference where an [oldValue] is replaced with a [newValue].
 * @since 3.0
 */
@JsExport
class UpdateDiff() : PrimitiveDiff() {

    @JsName("of")
    constructor(oldValue: Any?, newValue: Any?): this() {
        setRaw(OLD_VALUE_KEY, oldValue)
        setRaw(NEW_VALUE_KEY, newValue)
    }

    companion object UpdateDiff_C {
        /**
         * The [PlatformType] of [UpdateDiff].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(UpdateDiff::class).withPackageName(PACKAGE_NAME).withNameAsJsonType()
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is UpdateDiff && oldValue == other.oldValue && newValue == other.newValue)

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + (oldValue?.hashCode() ?: 0)
        result = 31 * result + (newValue?.hashCode() ?: 0)
        return result
    }
}
