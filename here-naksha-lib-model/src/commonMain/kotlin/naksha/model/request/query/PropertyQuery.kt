@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import naksha.base.AnyObject
import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * A property query. The storage manages [rows][naksha.model.Row], and every row has fields. Each field has a specific content, and can be queried. Some fields are structured, in that case a path is required, which is part of the property [Property].
 */
@JsExport
open class PropertyQuery() : AnyObject(), IPropertyQuery {
    /**
     * Create an initialized property query.
     * @param property the property to query.
     * @param op the operation to execute.
     * @param value the parameter value of the operation.
     */
    @JsName("of")
    constructor(property: Property, op: AnyOp, value: Any? = null) : this() {
        this.property = property
        this.op = op
        this.value = value
    }

    companion object PropertyQueryCompanion {
        private val PROPERTY = NotNullProperty<PropertyQuery, Property>(Property::class)
        private val QUERY_OP = NotNullProperty<PropertyQuery, AnyOp>(AnyOp::class)
        private val ANY = NullableProperty<PropertyQuery, Any>(Any::class)
    }

    /**
     * The property to query.
     */
    var property by PROPERTY

    /**
     * The operation to execute.
     */
    var op by QUERY_OP

    /**
     * The parameter value of the operation; if any.
     */
    var value by ANY
}
