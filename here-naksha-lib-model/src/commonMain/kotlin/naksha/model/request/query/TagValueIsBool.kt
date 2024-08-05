@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import naksha.base.NotNullProperty
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Tests if the tag with given name exists, and is equal to the given value.
 */
@JsExport
class TagValueIsBool() : TagQuery() {
    /**
     * Tests if the tag with given name exists, and is equal to the given value.
     * @param name the name of the tag.
     * @param value the value to test for.
     */
    @JsName("of")
    constructor(name: String, value: Boolean) : this() {
        this.name = name
        this.value = value
    }

    companion object TagExists_C {
        private val BOOLEAN = NotNullProperty<TagValueIsBool, Boolean>(Boolean::class) { _, _ -> false }
    }

    /**
     * The value.
     */
    var value by BOOLEAN
}