@file:Suppress("OPT_IN_USAGE")

package naksha.model.mom

import naksha.base.NullableProperty
import naksha.base.AnyObject
import kotlin.js.JsExport

/**
 * MOM reference object holding minimum equivalent fields from MOM reference object.
 */
@JsExport
class MomReference : AnyObject() {

    companion object MomReference_C {
        private val STRING_NULL = NullableProperty<MomReference, String>(String::class)
    }

    /**
     * The feature id that is referred.
     */
    var id by STRING_NULL

    /**
     * The space that is referred.
     */
    var spaceId by STRING_NULL

    /**
     * The feature-type is referred (is contained in the [id], because MOM defines IDs as URNs).
     */
    var featureType by STRING_NULL
}