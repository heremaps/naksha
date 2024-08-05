@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import naksha.base.NotNullProperty
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Tests if the tag with given name exists, and is equal to the given value.
 * @since 3.0.0
 */
@JsExport
class TagValueIsString() : TagQuery() {
    /**
     * Tests if the tag with given name exists, and is equal to the given value.
     * @param name the name of the tag.
     * @param value the value to test for.
     * @since 3.0.0
     */
    @JsName("of")
    constructor(name: String, value: String) : this() {
        this.name = name
        this.value = value
    }

    companion object TagValueIsString_C {
        private val STRING = NotNullProperty<TagValueIsString, String>(String::class) { _, _ -> "" }
    }

    /**
     * The value.
     * @since 3.0.0
     */
    var value by STRING
}
