package naksha.psql

import naksha.model.Naksha
import kotlin.js.JsExport

/**
 * Maintains partitions using a fresh attachment to the current database for every invocation. All work belongs to the invoking SQL transaction; failures escape to the caller without allocating a logical Naksha transaction or committing independently.
 * @return the number of maintained collections.
 * @since 3.0
 */
@JsExport
fun naksha_maintain(): Int {
  val options = Naksha.useAdminOptions()
  val instance = PgInstance.of(PgInstanceConfig().withPassword(""))
  val config = instance.openConnection(options, false, PgStorage.SKIP_INIT).use { conn ->
    PgLocalConfig.readFromSchemaComment(conn)
  }
  val storage = PgStorage(config, false, false)
  return storage.newSession(options, false).use { session ->
    session.useConnNonNull(false) { conn ->
      storage.database.maintain_partitions(conn)
    }
  }
}
