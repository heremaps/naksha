@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.ops

import naksha.base.PAnyArray
import naksha.base.NotNullProperty
import naksha.model.objects.Member
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Tests if all of the given [items] exist in the member at [at].
 * @since 3.0
 */
@JsExport
class TagListContainsAllOf() : Op() {
    companion object TagListHasAllOf_C {
        private val ITEMS = NotNullProperty<TagListContainsAllOf, PAnyArray>(PAnyArray::class) { _, _ -> PAnyArray() }
    }

    @JsName("forName")
    constructor(at: String, vararg items: Any) : this() {
        this.op = TAGLIST_CONTAINS_ALL_OF
        this.at = at
        val _items = this.items
        for (item in items) _items.add(item)
    }

    @JsName("forMember")
    constructor(at: Member, vararg items: Any) : this(at.id, *items)

    var items: PAnyArray by ITEMS
}
