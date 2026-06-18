@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import naksha.base.AnyObject
import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.model.objects.Member
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * A query about a specific [member][naksha.model.objects.Member].
 * @since 3.0
 */
@JsExport
open class MemberQuery() : AnyObject(), IMemberQuery {
    /**
     * Create an initialized property query.
     * @param column the column of the metadata to query.
     * @param op the operation to execute.
     * @param value the parameter value of the operation.
     * @since 3.0
     */
    @JsName("of")
    constructor(column: Member, op: AnyOp, value: Any? = null) : this() {
        this.column = column
        this.op = op
        this.value = value
    }

    companion object PropertyQueryCompanion {
        private val COLUMNS = NotNullProperty<MemberQuery, MetaColumn>(MetaColumn::class)
        private val QUERY_OP = NotNullProperty<MemberQuery, AnyOp>(AnyOp::class)
        private val ANY = NullableProperty<MemberQuery, Any>(Any::class)
    }

    /**
     * The column to query.
     */
    var column by COLUMNS

    /**
     * The operation to execute.
     */
    var op by QUERY_OP

    /**
     * The parameter value of the operation; if any.
     */
    var value by ANY
}