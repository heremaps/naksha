@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Tests if the tag with given name exists, ignoring the value.
 *
 * For map-form tags ([naksha.model.objects.MemberType.TAG_MAP] / [naksha.model.objects.MemberType.TAG_MAP_FROM_ARRAY])
 * this tests if the key exists. For tag-list-form tags ([naksha.model.objects.MemberType.TAG_LIST], the default)
 * this tests if the full string element exists, e.g. `TagExists("foo")` matches a feature tagged
 * `["foo", "bar"]`.
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
