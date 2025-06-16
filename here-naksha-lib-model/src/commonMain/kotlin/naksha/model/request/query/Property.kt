@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import naksha.base.NotNullProperty
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformListApi.PlatformListApi_C.list_get
import naksha.base.PlatformListApi.PlatformListApi_C.list_get_length
import naksha.base.PlatformType
import naksha.base.StringList
import naksha.base.fn.Fn1
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * The reference to a property within a feature.
 *
 * **Warning:** You should not search for the `id`, `geometry`, or anything from [`properties->@ns:com:here:xyz`][naksha.model.XyzNs] using this query, because there are specialized, and optimized, dedicated queries available. So avoid things like `PQuery(Property("id"), StringOp.EQUALS, "foo"`.
 * @see IQuery
 * @see IPropertyQuery
 * @see PQuery
 * @see Property
 * @see PAnd
 * @see POr
 * @see PNot
 */
@JsExport
open class Property() : MetaColumn(FEATURE) {

    /**
     * Create a property from a path given as variable argument list.
     * @param path the path-segments.
     */
    @JsName("PropertyOf")
    constructor(vararg path: String) : this() {
        for (p in path) this.path.add(p)
    }

    companion object Property_C {
        /**
         * The [PlatformType] of [Property].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(Property::class).withPackageName(PACKAGE_NAME)

        /**
         * Simple constant for `properties`.
         */
        const val PROPERTIES = "properties"

        /**
         * Simple constant for `@ns:com:here:xyz`.
         */
        const val XYZ = "@ns:com:here:xyz"

        const val TAGS = "tags"

        private val PATH = NotNullProperty<Property, StringList>(StringList.TYPE)
    }

    /**
     * The path inside the feature.
     */
    val path by PATH

    private var array: Array<String>? = null
    private var string: String? = null

    /**
     * Convert the property into a path like `name->name->name`, optionally using the given function to escape the names.
     * @param quote optional callback to quote the name-parts.
     * @return this property as path.
     */
    fun toPath(quote: Fn1<String, String>?): String {
        val po = path.platformObject()
        var array = this.array
        var s = string
        if (s != null) {
            if (array == null) {
                s = null
            } else if (array.size != list_get_length(po)) {
                s = null
            } else {
                for (i in array.indices) {
                    if (array[i] !== list_get(po, i)) {
                        s = null
                        break
                    }
                }
            }
        }
        if (s != null) return s
        // This happens if the platform object was modified since we were called last, or we're called for the first time.
        array = Array(list_get_length(po)) { list_get(po, it) as String }
        s = array.joinToString(separator = "->") { quote?.call(it) ?: it }
        this.array = array
        this.string = s
        return s
    }

    override fun toString(): String = toPath(null)

    override fun hashCode(): Int = platformObject().hashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Property) return false
        val po = path.platformObject()
        val other_po = other.path.platformObject()
        val length = list_get_length(po)
        if (length != list_get_length(other_po)) return false
        var i = 0
        while (i < length) {
            if (list_get(po, i) != list_get(other_po, i)) return false
            i++
        }
        return true
    }

}