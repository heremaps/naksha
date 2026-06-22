@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.ops

import naksha.base.AnyList
import naksha.base.NotNullProperty
import naksha.model.objects.Member
import kotlin.js.JsExport

/**
 * Tests if all of the given [items] exist in the member at [at].
 * @since 3.0
 */
@JsExport
class TagListHasAllOf() : Op() {
    companion object TagListHasAllOf_C {
        private val ITEMS = NotNullProperty<TagListHasAllOf, AnyList>(AnyList::class) { _,_ -> AnyList() }
    }

    constructor(at: String, vararg items: Any) : this() {
        this.op = TAGLIST_HAS_ALL_OF
        this.at = at
        val _items = this.items
        for (item in items) _items.add(item)
    }

    constructor(at: Member, vararg items: Any) : this(at.name, *items)

    var items: AnyList by ITEMS
}
