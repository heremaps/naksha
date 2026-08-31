@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Tests if the tag _(key)_ with given name exists in a [TagMap][naksha.model.TagMap] or [TagList][naksha.model.TagList], ignoring the value. For example `TagExists("foo")` matches a feature tagged `["foo", "bar"]` or `{"foo":5, "bar":"demo"}`.
 * @since 3.0.0
 */
@JsExport
class TagExists() : TagQuery() {

    /**
     * Tests if the tag with given name exists, ignoring the value.
     * @param name the name of the tag.
     * @since 3.0.0
     */
    @JsName("of")
    constructor(name: String) : this() {
        this.name = name
    }
}
