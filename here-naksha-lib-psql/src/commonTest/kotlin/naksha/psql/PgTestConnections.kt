package naksha.psql

import naksha.model.Naksha

/** Test-only convenience replacing the removed storage adminConnection API. */
internal fun PgStorage.adminConnection(): PgConnection =
    newConnection(Naksha.useAdminOptions(), false)
