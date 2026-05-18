@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.ListProxy
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.jvm.JvmStatic

/**
 * An ordered list of user-defined SQL columns on a [NakshaCollection].
 * @since 3.0
 */
@JsExport
open class CustomMemberList() : ListProxy<CustomMember>(CustomMember::class) {

    /**
     * Construct a list from a vararg of members.
     * @since 3.0
     */
    @JsName("fromMembers")
    constructor(vararg members: CustomMember) : this() {
        addAll(members.toList())
    }

    companion object CustomMemberList_C {
        /**
         * The maximum number of members allowed in a single collection.
         * @since 3.0
         */
        const val MAX_MEMBERS = 64

        @JvmStatic
        fun of(vararg members: CustomMember): CustomMemberList =
            CustomMemberList().apply { addAll(members.toList()) }
    }
}
