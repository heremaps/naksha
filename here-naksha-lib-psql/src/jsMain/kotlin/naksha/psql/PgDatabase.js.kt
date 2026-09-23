package naksha.psql

import naksha.base.Id
import naksha.base.unsupportedOp
import naksha.model.NakshaVersion

actual open class PgDatabase actual constructor(storage: PgStorage, instance: PgInstance, id: Id) :
    AbstractPgDatabase(storage, instance, id)
{
    actual override fun upsert_naksha_admin(
        conn: PgConnection,
        id: Id,
        psql_version: NakshaVersion,
        schema_oid: Int?,
        installed_version: NakshaVersion?
    ): Int {
        throw unsupportedOp("PgDatabase.upsert_naksha_admin")
    }
}