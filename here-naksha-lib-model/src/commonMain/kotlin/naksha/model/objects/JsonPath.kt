@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.ListProxy
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * An ordered list of object keys used to walk a JSON document.
 *
 * For example, `JsonPath("properties", "age")` resolves the value at `feature.properties.age`.
 *
 * Segments are arbitrary object keys — JSON allows any string as a key, including the XYZ
 * namespace identifier `@ns:com:here:xyz`. There is no array indexing in v3.0; paths can only
 * descend into object keys.
 * @since 3.0
 */
@JsExport
open class JsonPath() : ListProxy<String>(String::class) {

    /**
     * Construct a path from a vararg of segments.
     * @param segments the path segments.
     * @since 3.0
     */
    @JsName("fromSegments")
    constructor(vararg segments: String) : this() {
        addAll(segments.toList())
    }
}
