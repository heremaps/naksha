@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import naksha.base.AnyObject
import naksha.base.NullableProperty
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Tests if a tag-list-form tags member ([naksha.model.objects.MemberType.TAG_LIST], the default for the
 * standard `tags` member) contains the given element.
 *
 * The element is matched in its type: strings, booleans, and numbers are all supported. For string
 * elements this is equivalent to [TagExists], which can therefore be used interchangeably; for all
 * other primitives this query is the only way to search the set.
 *
 * Beware that a set stores full elements (the values are never split into key/value pairs), so only
 * complete elements can be matched: a feature tagged `["foo=bar"]` is found by
 * `TagSetContains("foo=bar")`, not by `TagSetContains("foo")`.
 *
 * This query does **not** match map-form tags ([naksha.model.objects.MemberType.TAGS] or
 * [naksha.model.objects.MemberType.TAGS_FROM_ARRAY]); use [TagExists] and the `TagValueIs*` queries
 * for those.
 * @since 3.0
 */
@JsExport
class TagSetContains() : AnyObject(), ITagQuery {

    /**
     * Tests if the set-form tags member contains the given element.
     * @param element the element to test for; must be a primitive (string, boolean, or number).
     * @since 3.0
     */
    @JsName("of")
    constructor(element: Any?) : this() {
        this.element = element
    }

    companion object TagSetContains_C {
        private val ELEMENT = NullableProperty<TagSetContains, Any>(Any::class)
    }

    /**
     * The element to test for; must be a primitive (string, boolean, or number).
     * @since 3.0
     */
    var element by ELEMENT
}
