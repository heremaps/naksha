@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.ops

import naksha.base.AnyList
import naksha.base.NotNullProperty
import naksha.model.objects.Member
import kotlin.js.JsExport

/**
 * Tests if the member at [at] is any of the given [items].
 * @since 3.0
 */
@JsExport
class IsAnyOf() : Op() {
    companion object IsAnyOf_C {
        private val ITEMS = NotNullProperty<IsAnyOf, AnyList>(AnyList::class) { _,_ -> AnyList() }
    }

    constructor(at: String, vararg items: Any) : this() {
        this.op = ANY_OF
        this.at = at
        val _items = this.items
        for (item in items) _items.add(item)
    }

    constructor(at: Member, vararg items: Any) : this(at.name, *items)

    var items: AnyList by ITEMS
}
