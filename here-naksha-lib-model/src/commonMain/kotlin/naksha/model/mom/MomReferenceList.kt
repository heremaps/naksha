@file:Suppress("OPT_IN_USAGE")

package naksha.model.mom

import naksha.base.ListProxy
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

/**
 * A list of MOM references.
 */
@JsExport
class MomReferenceList : ListProxy<MomReference>(MomReference::class) {
    companion object MomReferenceList_C {

        @JvmStatic
        fun fromList(features: List<MomReference>): MomReferenceList =
            MomReferenceList().apply { addAll(features) }

        @JvmStatic
        fun of(vararg features: MomReference): MomReferenceList =
            MomReferenceList().apply { addAll(features) }
    }
}