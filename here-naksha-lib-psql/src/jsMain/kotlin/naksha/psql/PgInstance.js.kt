package naksha.psql

import naksha.base.Platform
import naksha.base.fn.Fx2
import naksha.model.SessionOptions
import kotlin.math.max

actual class PgInstance private actual constructor(private val config: PgInstanceConfig) {
    actual companion object PgInstance_C {
        private val instances = HashMap<String, PgInstance>()
        private var connCount = 1L

        actual fun of(config: PgInstanceConfig): PgInstance {
            val url = config.toJdbcUri(true)
            var i = instances[url]
            if (i != null) return i
            i = PgInstance(config)
            instances[url] = i
            return i
        }
    }

    actual val host: String
        get() = config.host
    actual val port: Int
        get() = config.port
    actual val database: String
        get() = config.db
    actual val user: String
        get() = config.user
    actual val password: String
        get() = config.password
    actual val readOnly: Boolean
        get() = config.readOnly
    actual val url: String
        get() = config.toString()

    override fun toString(): String
        = config.toJdbcUri(false)

    actual var connectionLimit: Int = config.connectionLimit

    private fun toSeconds(millis: Int, minMillis: Int = 1000): Int = max(0, max(minMillis, millis) / 1000)

    actual fun openConnection(options: SessionOptions, readOnly: Boolean, init: Fx2<PgConnection, String>?): PgConnection {
        val conn = PgConnection(connCount++, this, options)
        val query = """SET SESSION $search_path TO "naksha~admin", hint_plan, public, topology;
SET SESSION $work_mem = '128MB';
SET SESSION $idle_session_timeout = '600s';
SET SESSION $statement_timeout = '${toSeconds(options.stmtTimeout)}s';
SET SESSION $lock_timeout = '${toSeconds(options.lockTimeout, 0)}s';
SET SESSION $idle_in_transaction_session_timeout = '${toSeconds(options.idleTxTimeout)}s';
SET SESSION enable_async_append = on;
SET SESSION max_parallel_workers = 128;
SET SESSION max_parallel_workers_per_gather = 16;
SET SESSION parallel_setup_cost = 1;   
SET SESSION parallel_tuple_cost = 0.01;
SET SESSION enable_partition_pruning = on;
SET SESSION enable_partitionwise_join = on;
SET SESSION enable_partitionwise_aggregate = on;
SET SESSION enable_gathermerge = on;
SET SESSION enable_seqscan = off;
SET SESSION enable_bitmapscan = on;
SET SESSION enable_indexscan = on;
SET SESSION enable_indexonlyscan = on;
SET SESSION enable_nestloop = on;
SET SESSION enable_sort = off;
SET SESSION pg_hint_plan.enable_hint = on;
"""
        if (init != null) {
            init.call(conn, query)
        } else {
            plv8.execute(query)
        }
        return conn
    }
}