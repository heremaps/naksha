package naksha.psql.executors

import naksha.base.fn.Fn1
import naksha.model.IMetadataArray
import naksha.psql.PgConnection

/**
 * Abstraction to allow synchronous executions the same way as parallel ones.
 */
interface IExecutor {
    /**
     * Execute the given target with a connection, collect the result and provide it, when it is ready.
     * @param target the method to call, which will produce synchronously a result.
     * @return the execution, which can be queried if the execution is finished.
     */
    fun execute(target: Fn1<PgConnection, IMetadataArray>): IExecution
}