@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * Tests if the tag with given name exists, ignoring the value.
 * @since 3.0
 * @see IQuery
 * @see ITagQuery
 * @see TagQuery
 */
@JsExport
class TagExists() : TagQuery() {

    /**
     * Tests if the tag with given name exists, ignoring the value.
     * @param name the name of the tag.
     * @since 3.0
     */
    @JsName("of")
    constructor(name: String) : this() {
        this.name = name
    }

    companion object TagExists_C {
        /**
         * The [PlatformType] of [TagExists].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(TagExists::class).withPackageName(PACKAGE_NAME)
    }
}
