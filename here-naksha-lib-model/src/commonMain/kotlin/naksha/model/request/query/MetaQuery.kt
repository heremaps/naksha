@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import naksha.base.AnyObject
import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * A meta-data query within the Naksha feature.
 */
@JsExport
open class MetaQuery() : AnyObject(), IMetaQuery {
    /**
     * Create an initialized property query.
     * @param column the column of the metadata to query.
     * @param op the operation to execute.
     * @param value the parameter value of the operation.
     */
    @JsName("of")
    constructor(column: TupleColumn, op: AnyOp, value: Any? = null) : this() {
        this.column = column
        this.op = op
        this.value = value
    }

    companion object PropertyQueryCompanion {
        private val COLUMNS = NotNullProperty<MetaQuery, TupleColumn>(TupleColumn::class)
        private val QUERY_OP = NotNullProperty<MetaQuery, AnyOp>(AnyOp::class)
        private val ANY = NullableProperty<MetaQuery, Any>(Any::class)
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