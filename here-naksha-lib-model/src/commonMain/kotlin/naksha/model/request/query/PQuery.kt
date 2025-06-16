@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import naksha.base.*
import naksha.base.Platform.Platform_C.forKClass
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads

/**
 * A property query within the Naksha feature.
 * @since 3.0
 * @see IQuery
 * @see IPropertyQuery
 * @see PQuery
 * @see Property
 */
@JsExport
open class PQuery() : AnyObject(), IPropertyQuery {
    /**
     * Create an initialized property query.
     * @param property the property to query.
     * @param op the operation to execute.
     * @param value the parameter value of the operation.
     */
    @JsName("PQueryOf")
    @JvmOverloads
    constructor(property: Property, op: AnyOp, value: Any? = null) : this() {
        this.property = property
        this.op = op
        this.value = value
    }

    companion object PQuery_C {
        /**
         * The [PlatformType] of [PQuery].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(PQuery::class).withPackageName(PACKAGE_NAME)

        private val PROPERTY = NotNullProperty<PQuery, Property>(Property.TYPE)
        private val QUERY_OP = NotNullProperty<PQuery, AnyOp>(AnyOp.TYPE)
        private val ANY = NullableProperty<PQuery, Any>(Any_TYPE)
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
