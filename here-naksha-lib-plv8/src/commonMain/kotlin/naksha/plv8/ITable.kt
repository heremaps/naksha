@file:OptIn(ExperimentalJsExport::class)

package naksha.plv8

import naksha.base.AbstractMapProxy
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * When a function returns a table, then PLV8 will create a
 * [tuple-store](https://github.com/postgres/postgres/blob/master/src/backend/utils/sort/tuplestore.c) for this.
 * To return the rows, the function has to invoke `plv8.return_next(object)`. To optimize performance,
 * we will directly ask to use native objects here. In PostgresQL this design allows to read results while
 * they are produced.
 *
 * For the PostgresQL implementation, this interface does nothing, but redirecting calls to `plv8.return_next(object)`.,
 * For the JVM implementation it will create an in-memory virtual tuple-store. The function, e.g. [NakshaSession.writeFeatures],
 * will then return the table so that the results can be verified.
 */
@Suppress("DuplicatedCode")
@JsExport
@Deprecated(message = "not in use anymore as we operate on Row object and read full response at once")
interface ITable {
    /**
     * Returns a new row.
     * @param ret The return row.
     */
    fun returnNext(ret: AbstractMapProxy<String, Any>)
}