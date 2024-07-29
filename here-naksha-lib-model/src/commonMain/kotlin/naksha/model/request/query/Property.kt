@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import naksha.base.PlatformListApi.PlatformListApiCompanion.array_get
import naksha.base.PlatformListApi.PlatformListApiCompanion.array_get_length
import naksha.base.StringList
import naksha.model.NakshaUtil.NakshaUtilCompanion.quoteLiteral
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * The reference to a property within a feature.
 */
@JsExport
open class Property() : StringList() {
    /**
     * Create a property from a path given as variable argument list.
     * @param path the path-segments.
     */
    @Suppress("LeakingThis")
    @JsName("of")
    constructor(vararg path: String) : this() {
        for (p in path) add(p)
    }

    /**
     * Create a property from a path given as variable argument list.
     * @param path the path as string-array.
     */
    @JsName("fromArray")
    constructor(path: Array<String>) : this() {
        addAll(path)
    }

    private var array: Array<String>? = null
    private var string: String? = null

    override fun toString(): String {
        val po = platformObject()
        var array = this.array
        var s = string
        if (s != null) {
            if (array == null) {
                s = null
            } else if (array.size != array_get_length(po)) {
                s = null
            } else {
                for (i in array.indices) {
                    if (array[i] !== array_get(po, i)) {
                        s = null
                        break
                    }
                }
            }
        }
        if (s != null) return s
        // This happens if the platform object was modified since we were called last, or we're called for the first time.
        array = Array(array_get_length(po)) { array_get(po, it) as String }
        s = array.joinToString(separator = "->") { quoteLiteral(it) }
        this.array = array
        this.string = s
        return s
    }
    override fun hashCode(): Int = platformObject().hashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Property) return false
        val po = platformObject()
        val other_po = other.platformObject()
        val length = array_get_length(po)
        if (length != array_get_length(other_po)) return false
        var i = 0
        while (i < length) {
            if (array_get(po, i) != array_get(other_po, i)) return false
            i++
        }
        return true
    }

    companion object Property_C {
        /**
         * Simple constant for `properties`.
         */
        const val PROPERTIES = "properties"

        /**
         * Simple constant for `@ns:com:here:xyz`.
         */
        const val XYZ = "@ns:com:here:xyz"
    }
}