@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import naksha.base.AnyObject
import naksha.base.NotNullProperty
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import naksha.base.String_TYPE
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A general form of a tag query without any operation.
 * @since 3.0
 * @see IQuery
 * @see ITagQuery
 * @see TagQuery
 * @see TagExists
 * @see TagValueIsBool
 * @see TagValueIsDouble
 * @see TagValueIsNull
 * @see TagValueIsString
 * @see TagValueMatches
 */
@JsExport
open class TagQuery internal constructor(): AnyObject(), ITagQuery {

    companion object TagQuery_C {
        /**
         * The [PlatformType] of [TagQuery].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(TagQuery::class).withPackageName(PACKAGE_NAME)

        private val STRING = NotNullProperty<TagQuery, String>(String_TYPE) { _, _ -> "" }
    }

    /**
     * The name of the tag to test.
     * @since 3.0
     */
    var name by STRING
}