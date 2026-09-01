@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.ops

import naksha.base.NotNullProperty
import naksha.model.objects.Member
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Tests if the given [item] exist in the member at [at], expects the member to be a tag-list.
 * @since 3.0
 */
@JsExport
class TagListContains() : Op() {
    companion object TagListHasAllOf_C {
        private val ITEM = NotNullProperty<TagListContains, Any>(Any::class)
    }

    @JsName("forName")
    constructor(at: String, item: Any) : this() {
        this.op = TAGLIST_CONTAINS
        this.at = at
        this.item = item
    }

    @JsName("forMember")
    constructor(at: Member, item: Any) : this() {
        this.op = TAGLIST_CONTAINS
        this.at = at.name
        this.item = item
    }

    var item: Any by ITEM
}
