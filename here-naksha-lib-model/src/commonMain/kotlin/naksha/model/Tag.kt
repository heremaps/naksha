@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.*
import naksha.base.NormalizerForm.NFD
import naksha.base.NormalizerForm.NFKC
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * An immutable mapper for a split tag.
 * @property tag the full tag.
 * @property key the key of the tag.
 * @property value the value of the tag; _null_, Boolean, String or Double.
 */
@JsExport
class Tag() : AnyObject() {

    @JsName("of")
    constructor(tag: String, key: String, value: Any?) : this() {
        this.tag = tag
        this.key = key
        this.value = value
    }

    companion object Tag_C {
        private val TAG = NotNullProperty<Tag, String>(String::class)
        private val KEY = NotNullProperty<Tag, String>(String::class)
        private val VALUE = NullableProperty<Tag, Any>(Any::class)

        @JvmStatic
        @JsStatic
        fun of(normalizedKey: String, normalizedValue: Any?): Tag = when (normalizedValue) {
            null -> Tag(normalizedKey, normalizedKey, null)
            is String -> Tag("$normalizedKey=$normalizedValue", normalizedKey, normalizedValue)
            is Boolean -> Tag("$normalizedKey:=$normalizedValue", normalizedKey, normalizedValue)
            is Number -> {
                val doubleValue = normalizedValue.toDouble()
                Tag("$normalizedKey:=$doubleValue", normalizedKey, doubleValue)
            }

            else -> throw NakshaException(
                ILLEGAL_ARGUMENT,
                "Tag values can only be String, Boolean or Number"
            )
        }
    }

    var tag by TAG
    var key by KEY
    var value by VALUE

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other is String) return tag == other
        if (other is Tag) return tag == other.tag
        return false
    }

    override fun hashCode(): Int = tag.hashCode()
    override fun toString(): String = tag
}