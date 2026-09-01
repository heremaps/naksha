@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import naksha.base.AnyObject
import naksha.base.NotNullProperty
import kotlin.js.JsExport

/**
 * A general form of a tag query without any operation.
 *
 * Note: the `TagValueIs*` and [TagValueMatches] queries operate on keys/values and therefore only
 * match map-form tags ([naksha.model.objects.MemberType.TAG_MAP] /
 * [naksha.model.objects.MemberType.TAG_MAP_FROM_ARRAY]). Against the default set-form tags
 * ([naksha.model.objects.MemberType.TAG_LIST]) the values are never split into key/value pairs; use
 * [TagExists] or [TagSetContains] to match full elements instead.
 * @since 3.0.0
 * @see TagExists
 * @see TagSetContains
 * @see TagValueIsBool
 * @see TagValueIsDouble
 * @see TagValueIsNull
 * @see TagValueIsString
 * @see TagValueMatches
 */
@JsExport
open class TagQuery internal constructor(): AnyObject(), ITagQuery {

    companion object TagQuery_C {
        private val STRING = NotNullProperty<TagQuery, String>(String::class) { _,_ -> "" }
    }

    /**
     * The name of the tag to test.
     * @since 3.0.0
     */
    var name by STRING
}