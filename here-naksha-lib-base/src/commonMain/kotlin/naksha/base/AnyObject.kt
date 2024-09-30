package naksha.base

import kotlin.js.JsExport

/**
 * The map where the key is [String] and the value can be anything. This is basically what objects normally look like.
 * - [AnyList]
 * - [AnyMap]
 * - [AnyObject]
 */
@Suppress("unused", "OPT_IN_USAGE")
@JsExport
open class AnyObject : MapProxy<String, Any>(String::class, Any::class) {
    fun contentDeepEquals(other: AnyObject): Boolean {
        if (this.size != other.size) return false
        for ((key, value1) in this.entries) {
            val value2 = other[key]
            if (value1 is Array<*> && value2 is Array<*>) {
                if (!value1.contentDeepEquals(value2)) return false
            } else if (value1 != value2) {
                return false
            }
        }
        return true
    }
}


