@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Tests if the tag is assigned to the value _null_.
 * @since 3.0.0
 */
@JsExport
class TagValueIsNull() : TagQuery() {

    /**
     * Tests if the tag is assigned to the value _null_.
     * @param name the name of the tag.
     * @since 3.0.0
     */
    @JsName("of")
    constructor(name: String) : this() {
        this.name = name
    }
}

