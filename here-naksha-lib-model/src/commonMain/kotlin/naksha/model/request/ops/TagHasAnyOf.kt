@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.ops

import naksha.base.NotNullProperty
import naksha.base.StringList
import naksha.model.objects.Member
import kotlin.js.JsExport

/**
 * Tests if any of the given [keys] exist on the member at [at].
 * @since 3.0
 */
@JsExport
class TagHasAnyOf() : Op() {
    companion object TagHasAnyOf_C {
        private val KEYS = NotNullProperty<TagHasAnyOf, StringList>(StringList::class) { _,_ -> StringList() }
    }

    constructor(at: String, vararg keys: String) : this() {
        this.op = TAG_HAS_ANY_OF
        this.at = at
        val _tagKeys = this.tagKeys
        for (key in keys) _tagKeys.add(key)
    }

    constructor(at: Member, vararg keys: String) : this(at.name, *keys)

    var tagKeys: StringList by KEYS
}
