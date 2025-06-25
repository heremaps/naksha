@file:Suppress("OPT_IN_USAGE")

package naksha.base

import naksha.base.Platform.Platform_C.forKClass
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A simple helper class to have mutable, not thread safe, doubles.
 * @since 3.0
 */
@JsExport
data class MutableDouble(
    /**
     * The value, can be mutated.
     * @since 3.0
     */
    var value: Double = 0.0
) : JsonValue {
    companion object MutableDouble_C {
        /**
         * The [PlatformType] of [MutableDouble].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(MutableDouble::class).withPackageName(PACKAGE_NAME)

        init { initialize() }
    }

    /**
     * Add the given `value` to this, and return this.
     * @param value the value to add.
     * @return this.
     * @since 3.0
     */
    operator fun plus(value: Double): MutableDouble {
        this.value += value
        return this
    }

    /**
     * Subtract the given `value` from this, and return this.
     * @param value the value to subtract.
     * @return this.
     * @since 3.0
     */
    operator fun minus(value: Double): MutableDouble {
        this.value -= value
        return this
    }

    /**
     * Multiply this with the given `value`, and return this.
     * @param value the value to multiply with.
     * @return this.
     * @since 3.0
     */
    operator fun times(value: Double): MutableDouble {
        this.value *= value
        return this
    }

    /**
     * Divide this by the given `value`, and return this.
     * @param value the value to divide by.
     * @return this.
     * @since 3.0
     */
    operator fun div(value: Double): MutableDouble {
        this.value /= value
        return this
    }

    override val jsonValue: Double
        get() = value
    override fun duplicate(): MutableDouble = MutableDouble(value)

    override fun hashCode(): Int = value.toInt()
    override fun equals(other: Any?): Boolean = other is MutableDouble && value == other.value
    private var _cacheValue: Double = 0.0
    private var _cacheString: String? = null

    override fun toString(): String {
        val _cacheValue = this._cacheValue
        var _cacheString = this._cacheString
        if (_cacheString != null && value == _cacheValue) return _cacheString
        _cacheString = value.toString()
        // Hack to align Java and JavaScript (JavaScript serializes 5.0 to "5")
        if (!_cacheString.contains('.')) _cacheString += ".0"
        this._cacheValue = value
        this._cacheString = _cacheString
        return _cacheString
    }
}