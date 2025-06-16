@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import naksha.base.NotNullProperty
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import naksha.base.String_TYPE
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * Tests if the tag with given name exists, and is equal to the given value.
 * @since 3.0
 * @see IQuery
 * @see ITagQuery
 * @see TagQuery
 */
@JsExport
class TagValueIsString() : TagQuery() {
    /**
     * Tests if the tag with given name exists, and is equal to the given value.
     * @param name the name of the tag.
     * @param value the value to test for.
     * @since 3.0
     */
    @JsName("of")
    constructor(name: String, value: String) : this() {
        this.name = name
        this.value = value
    }

    companion object TagValueIsString_C {
        /**
         * The [PlatformType] of [TagValueIsString].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(TagValueIsString::class).withPackageName(PACKAGE_NAME)

        private val STRING = NotNullProperty<TagValueIsString, String>(String_TYPE) { _, _ -> "" }
    }

    /**
     * The value.
     * @since 3.0
     */
    var value by STRING
}
