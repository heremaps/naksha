@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.nak

import kotlin.js.JsExport

/**
 * The base class for all multi-platform types.
 * @param <P> The platform type, being one of: [PObject], [PArray] or [PDataView].
 * @property data The data object to which this type is bound.
 * @property symbol The symbol to which this type is bound.
 */
@JsExport
abstract class NakType<P>(val data: P) {
    /**
     * Returns the Naksha class of this Naksha type, should always be implemented by returning the value of the static member
     * with the name **klass**.
     * @return The Naksha class.
     */
    abstract fun nakClass(): NakClass<P, *>
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
