@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * Tests if the tag is assigned to the value _null_.
 * @since 3.0
 * @see IQuery
 * @see ITagQuery
 * @see TagQuery
 */
@JsExport
class TagValueIsNull() : TagQuery() {

    /**
     * Tests if the tag is assigned to the value _null_.
     * @param name the name of the tag.
     * @since 3.0
     */
    @JsName("of")
    constructor(name: String) : this() {
        this.name = name
    }

    companion object TagValueIsNull_C {
        /**
         * The [PlatformType] of [TagValueIsNull].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(TagValueIsNull::class).withPackageName(PACKAGE_NAME)
    }
}

