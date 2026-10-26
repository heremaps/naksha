package naksha.psql

import naksha.base.Id
import naksha.base.fn.Fn0
import naksha.base.fn.Fx0
import naksha.model.NakshaVersion

/**
 * A concrete implementation of [PgDatabase] based upon JVM or PLv8.
 *
 * This class is created by [PgDatabase] and **must not** be used externally.
 * @param storage the [storage][PgStorage] that contains the database.
 * @param id the [Id] of the database. For downward compatibility currently this should be the same as the storage-id.
 * @since 3.0
 * @see PgStorage
 * @see PgDatabase
 */
expect open class PgDatabaseImpl internal constructor(storage: PgStorage, id: Id): PgDatabase {

    override fun upsert_naksha_admin(
        conn: PgConnection,
        id: Id,
        psql_version: NakshaVersion,
        schema_oid: Int?,
        installed_version: NakshaVersion?
    ): Int
    override fun <R> execute_sql_nullable(schema: String?, table: String?, code: Fn0<R?>): R?
    override fun <R> execute_sql_notnull(schema: String?, table: String?, code: Fn0<R>): R
    override fun execute_sql(schema: String?, table: String?, code: Fx0)
}