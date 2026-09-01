@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.AnyList
import naksha.base.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.base.NakshaException
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * An ordered list of keys used to walk a JSON document. All entries must be either integer or strings, all other values are invalid.
 *
 * For example, `JsonPath("properties", "age")` resolves the value at `properties.age` or `JsonPath("properties", "data", 0)` resolves the value at `properties.data[0]` with `data` expected to be an array.
 * @since 3.0
 */
@JsExport
open class JsonPath() : AnyList() {

    /**
     * Construct a path from a vararg of segments.
     * @param segments the path segments.
     * @since 3.0
     */
    @JsName("fromSegments")
    constructor(vararg segments: Any) : this() {
        for (segment in segments) add(segment)
    }

    /**
     * Tests whether the path is valid, if not, throws an [NakshaException] with [ILLEGAL_STATE] error. Actually, the path must only contain strings and integers.
     * @return this.
     * @since 3.0
     */
    fun validate(): JsonPath {
        for (i in 0 until this.size) {
            val segment = this[i] ?: throw NakshaException(ILLEGAL_STATE, "Illegal NULL at path position $i")
            if (segment !is String && segment !is Int) {
                throw NakshaException(ILLEGAL_STATE, "Illegal value at path position $i, must be string or integer, found: '$segment'")
            }
        }
        return this
    }
}