@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import naksha.base.*
import naksha.base.Platform.Platform_C.forKClass
import naksha.model.request.RequestQuery
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A meta-data query within the Naksha feature.
 * @since 3.0
 * @see IQuery
 * @see IMetaQuery
 * @see MetaColumn
 */
@JsExport
open class MetaQuery() : AnyObject(), IMetaQuery {
    /**
     * Create an initialized property query.
     * @param column the column of the metadata to query.
     * @param op the operation to execute.
     * @param value the parameter value of the operation.
     * @since 3.0
     */
    @JsName("MetaQueryOf")
    constructor(column: MetaColumn, op: AnyOp, value: Any? = null) : this() {
        this.column = column
        this.op = op
        this.value = value
    }

    companion object MetaQuery_C {
        /**
         * The [PlatformType] of [MetaQuery].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(MetaQuery::class).withPackageName(PACKAGE_NAME)

        private val COLUMNS = NotNullProperty<MetaQuery, MetaColumn>(MetaColumn.TYPE)
        private val QUERY_OP = NotNullProperty<MetaQuery, AnyOp>(AnyOp.TYPE)
        private val ANY = NullableProperty<MetaQuery, Any>(Any_TYPE)
    }

    /**
     * The column to query.
     */
    var column by COLUMNS

    /**
     * The operation to execute.
     * @see AnyOp
     */
    var op by QUERY_OP

    /**
     * The parameter value of the operation; if any.
     */
    var value by ANY
}