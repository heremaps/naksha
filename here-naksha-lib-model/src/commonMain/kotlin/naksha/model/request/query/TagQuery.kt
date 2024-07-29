@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import naksha.base.AnyObject
import naksha.base.NotNullProperty
import kotlin.js.JsExport

/**
 * A general form of a tag query without any operation.
 * @since 3.0.0
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