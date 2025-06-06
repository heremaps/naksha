@file:Suppress("OPT_IN_USAGE")

package naksha.base

import naksha.base.Platform.PlatformCompanion.forKClass
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A simple helper class to have mutable, not thread safe, integers.
 * @since 3.0
 */
@JsExport
data class IntMutable(
    /**
     * The value, can be mutated.
     * @since 3.0
     */
    var value: Int = 0
) {
    companion object IntMutableCompanion {
        /**
         * The [PlatformType] of [IntMutable].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<IntMutable> = forKClass(IntMutable::class).withPackageName(PACKAGE_NAME)
    }

    /**
     * Add the given `value` to this, and return this.
     * @param value the value to add.
     * @return this.
     * @since 3.0
     */
    operator fun plus(value: Int): IntMutable {
        this.value += value
        return this
    }

    /**
     * Subtract the given `value` from this, and return this.
     * @param value the value to subtract.
     * @return this.
     * @since 3.0
     */
    operator fun minus(value: Int): IntMutable {
        this.value -= value
        return this
    }

    /**
     * Multiply this with the given `value`, and return this.
     * @param value the value to multiply with.
     * @return this.
     * @since 3.0
     */
    operator fun times(value: Int): IntMutable {
        this.value *= value
        return this
    }

    /**
     * Divide this by the given `value`, and return this.
     * @param value the value to divide by.
     * @return this.
     * @since 3.0
     */
    operator fun div(value: Int): IntMutable {
        this.value /= value
        return this
    }

    override fun hashCode(): Int = value
    override fun equals(other: Any?): Boolean = other is IntMutable && value == other.value
    private var _cacheValue: Int = 0
    private var _cacheString: String? = null

    override fun toString(): String {
        val _cacheValue = this._cacheValue
        var _cacheString = this._cacheString
        if (_cacheString != null && value == _cacheValue) return _cacheString
        _cacheString = value.toString()
        this._cacheValue = value
        this._cacheString = _cacheString
        return _cacheString
    }
}