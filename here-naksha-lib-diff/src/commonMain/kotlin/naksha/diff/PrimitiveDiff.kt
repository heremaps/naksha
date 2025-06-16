@file:Suppress("OPT_IN_USAGE")

package naksha.diff

import naksha.base.Any_TYPE
import naksha.base.AnyObject
import naksha.base.NullableProperty
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * The base class for all primitive operation.
 *
 * @since 3.0
 * @see InsertDiff
 * @see UpdateDiff
 * @see RemoveDiff
 */
@JsExport
abstract class PrimitiveDiff(): AnyObject(), Difference {

    @JsName("of")
    protected constructor(old: Any?, new: Any?): this() {
        set(OLD_VALUE_KEY, old)
        set(NEW_VALUE_KEY, new)
    }

    companion object PrimitiveDiff_C {
        /**
         * The [PlatformType] of [PrimitiveDiff].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(PrimitiveDiff::class).withPackageName(PACKAGE_NAME)

        /**
         * The name of the [oldValue] property.
         * @since 3.0
         */
        const val OLD_VALUE_KEY = "oldValue"

        /**
         * The name of the [newValue] property.
         * @since 3.0
         */
        const val NEW_VALUE_KEY = "newValue"

        private val ANY_VALUE = NullableProperty<PrimitiveDiff, Any>(Any_TYPE)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as PrimitiveDiff

        if (oldValue != other.oldValue) return false
        if (newValue != other.newValue) return false

        return true
    }

    override fun hashCode(): Int {
        var result = oldValue?.hashCode() ?: 0
        result = 31 * result + (newValue?.hashCode() ?: 0)
        return result
    }

    /**
     * The old value.
     * @since 3.0
     */
    open val oldValue: Any? by ANY_VALUE

    /**
     * The new value.
     * @since 3.0
     */
    open val newValue: Any? by ANY_VALUE
}