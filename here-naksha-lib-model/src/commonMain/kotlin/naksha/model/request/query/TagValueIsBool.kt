@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import naksha.base.Boolean_TYPE
import naksha.base.NotNullProperty
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
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

    companion object TagValueIsBool_C {
        /**
         * The [PlatformType] of [TagValueIsBool].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(TagValueIsBool::class).withPackageName(PACKAGE_NAME)

        private val BOOLEAN = NotNullProperty<TagValueIsBool, Boolean>(Boolean_TYPE) { _, _ -> false }
    }

    /**
     * The value.
     */
    var value by BOOLEAN
}