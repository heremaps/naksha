@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.*
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
class Tag(): AnyObject() {

    @JsName("of")
    constructor(tag: String, key: String, value: Any?): this() {
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
        fun parse(tag: String): Tag {
            val i = tag.indexOf('=')
            val key: String
            val value: Any?
            if (i > 1) {
                if (tag[i-1] == ':') { // :=
                    key = tag.substring(0, i-1).trim()
                    val raw = tag.substring(i + 1).trim()
                    value = if ("true".equals(raw, ignoreCase = true)) {
                        true
                    } else if ("false".equals(raw, ignoreCase = true)) {
                        false
                    } else {
                        raw. toDouble()
                    }
                } else {
                    key = tag.substring(0, i).trim()
                    value = tag.substring(i + 1).trim()
                }
            } else {
                key = tag
                value = null
            }
            return Tag(tag, key, value)
        }

        @JvmStatic
        @JsStatic
        fun of(key: String, value: Any?): Tag = when(value) {
            // TODO: Fix normalization!
            null -> Tag(key, key, null)
            is String -> Tag("$key=$value", key, value)
            is Boolean, Double -> Tag("$key:=$value", key, value)
            is Number -> of(key, value.toDouble())
            is Int64 -> of(key, value.toDouble())
            else -> throw NakshaException(ILLEGAL_ARGUMENT, "Tag values can only be String, Boolean or Double")
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