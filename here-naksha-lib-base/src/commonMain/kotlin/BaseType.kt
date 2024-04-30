@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport

/**
 * The base class for all assignment types.
 * @param <P> The platform type, being one of: [PObject], [PArray] or [PDataView].
 * @property data The platform object to which this type is bound.
 * @property symbol The symbol to which this type is bound.
 */
@JsExport
abstract class BaseType {
    companion object {
        val klass = object : BaseKlass<BaseType>() {
            override fun getPlatformKlass(): Klass<Any> = anyKlass

            override fun isAbstract(): Boolean = true

            override fun isArray(): Boolean = false

            override fun isInstance(o: Any?): Boolean = o is BaseType

            override fun newInstance(vararg args: Any?): BaseType = throw UnsupportedOperationException()
        }
    }

    /**
     * Returns the Klass of this instance.
     * @return The Klass of this instance.
     */
    abstract fun klass(): BaseKlass<*>

    /**
     * The data object to which this class is bound. Is late bound by [Base].
     */
    internal var data: Any? = null

    /**
     * Returns the data object to which this assignment type is bound.
     * @return The data object to which this assignment type is bound.
     */
    abstract fun data(): Any
}

//
// We should add a primary keys to all tables for logical replication!
// CREATE TABLE (
//   col,
//   col,
//   PRIMARY KEY (column_name [, ... ]) index_parameters
// index_parameters in UNIQUE, PRIMARY KEY, and EXCLUDE constraints are:
//      [ INCLUDE ( column_name [, ... ] ) ]
//      [ WITH ( storage_parameter [= value] [, ... ] ) ]
//      [ USING INDEX TABLESPACE tablespace_name ]
//
