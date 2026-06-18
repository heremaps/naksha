@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import naksha.base.AnyObject
import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.model.objects.Member
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Query a [member][naksha.model.objects.Member].
 * @since 3.0
 */
@JsExport
open class MemberQuery() : AnyObject(), IMemberQuery {
    /**
     * Create an initialized member query.
     * @param member the member to query.
     * @param op the operation to execute.
     * @param value the parameter value of the operation.
     * @since 3.0
     */
    @JsName("of")
    constructor(member: Member, op: AnyOp, value: Any? = null) : this() {
        this.member = member
        this.op = op
        this.value = value
    }

    companion object MemberQuery_C {
        private val MEMBERS = NotNullProperty<MemberQuery, Member>(Member::class)
        private val QUERY_OP = NotNullProperty<MemberQuery, AnyOp>(AnyOp::class)
        private val ANY = NullableProperty<MemberQuery, Any>(Any::class)
    }

    /**
     * The column to query.
     */
    var member: Member by MEMBERS

    /**
     * The operation to execute.
     */
    var op: AnyOp by QUERY_OP

    /**
     * The parameter value of the operation; if any.
     */
    var value: Any? by ANY
}