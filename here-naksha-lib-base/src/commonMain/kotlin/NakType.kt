@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.nak

import kotlin.js.JsExport

/**
 * The base class for all assignment types.
 * @param <P> The platform type, being one of: [PObject], [PArray] or [PDataView].
 * @property data The platform object to which this type is bound.
 * @property symbol The symbol to which this type is bound.
 */
@JsExport
abstract class NakType {
    companion object {
        val klass = object : NakKlass<NakType>() {
            override fun getPlatformKlass(): Klass<Any> = anyKlass

            override fun isAbstract(): Boolean = true

            override fun isArray(): Boolean = false

            override fun isInstance(o: Any?): Boolean = o is NakType

            override fun newInstance(vararg args: Any?): NakType = throw UnsupportedOperationException()
        }
    }

    /**
     * Returns the Klass of this instance.
     * @return The Klass of this instance.
     */
    abstract fun getKlass(): NakKlass<*>

    /**
     * The data object to which this class is bound. Is late bound by [Nak].
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
