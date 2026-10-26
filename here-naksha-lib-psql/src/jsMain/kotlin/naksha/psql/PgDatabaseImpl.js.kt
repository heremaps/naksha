package naksha.psql

import naksha.base.Id
import naksha.base.fn.Fn0
import naksha.base.fn.Fx0
import naksha.base.unsupportedOp
import naksha.model.NakshaVersion

actual open class PgDatabaseImpl internal actual constructor(storage: PgStorage, id: Id): PgDatabase(storage, id) {
    actual override fun upsert_naksha_admin(
        conn: PgConnection,
        id: Id,
        psql_version: NakshaVersion,
        schema_oid: Int?,
        installed_version: NakshaVersion?
    ): Int {
        throw unsupportedOp("PgDatabase.upsert_naksha_admin")
    }

    actual override fun <R> execute_sql_nullable(schema: String?, table: String?, code: Fn0<R?>): R? {
        return code.call()
    }

    actual override fun <R> execute_sql_notnull(schema: String?, table: String?, code: Fn0<R>): R {
        return code.call()
    }

    actual override fun execute_sql(schema: String?, table: String?, code: Fx0) {
        code.call()
    }
}