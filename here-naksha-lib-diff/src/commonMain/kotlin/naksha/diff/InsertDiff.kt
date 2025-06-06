@file:Suppress("OPT_IN_USAGE")

package naksha.diff

import naksha.base.Platform.PlatformCompanion.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A difference where a [newValue] was added.
 * @since 3.0
 */
@JsExport
class InsertDiff() : PrimitiveDiff() {

    @JsName("of")
    constructor(newValue: Any?): this() {
        set(NEW_VALUE_KEY, newValue)
    }

    companion object InsertDiffCompanion {
        /**
         * The [PlatformType] of [InsertDiff].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(InsertDiff::class).withPackageName(PACKAGE_NAME).withNameAsJsonType()
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is InsertDiff && newValue == other.newValue)

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + (newValue?.hashCode() ?: 0)
        return result
    }
}