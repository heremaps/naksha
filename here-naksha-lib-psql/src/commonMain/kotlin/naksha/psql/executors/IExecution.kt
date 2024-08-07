package naksha.psql.executors

import naksha.model.IMetadataArray
import naksha.psql.PgConnection

/**
 * Internal interface to access a job returned by [IExecutor].
 */
interface IExecution {
    /**
     * Returns the connection the execution uses.
     */
    fun getConnection(): PgConnection

    /**
     * Waits for the results of the underlying target.
     * @param waitMillis the amount of milliseconds to wait for the result; `0` to wait forever, `-1` to not wait at all.
     * @return the result, if available.
     */
    fun getResult(waitMillis: Int = -1): IMetadataArray?
}