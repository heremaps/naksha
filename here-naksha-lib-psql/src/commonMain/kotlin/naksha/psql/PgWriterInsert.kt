package naksha.psql

import naksha.base.Platform
import naksha.base.Platform.PlatformCompanion.logger
import naksha.base.illegalArg
import naksha.model.NewTuple

/**
 * Execute an INSERT through [PgDatabase], including re-creation after a DELETE HEAD.
 * @since 3.0
 * @see [PgWriter]
 */
internal class PgWriterInsert(
  private val pgWriter: PgWriter,
  private val pgCollection: PgCollection,
  private val pgWrites: List<PgWrite>,
  private val start: Int,
  private val end: Int
) {
  /**
   * Persist the selected writes and assign their returned states in input order.
   * @since 3.0
   */
  fun execute(conn: PgConnection) {
    if (start == end) return
    val tuples = ArrayList<NewTuple>(end - start)
    for (i in start ..< end) {
      val tuple = pgWrites[i].tuple ?: throw illegalArg("The write #$i has no tuple")
      tuples.add(NewTuple(tuple))
    }
    val session = pgWriter.session
    pgWrites[start].dbCatalog.applySearchPath(conn)
    // TODO(db-06 logging): wire session.logQueries/logExplain through PgDatabase diagnostics.
    val started = Platform.currentNanos()
    val result = session.storage.database.rows_insert(
      conn, pgCollection.catalogId, pgCollection.id, tuples, pgCollection.storeHistory, pgCollection.columns
    )
    val elapsed = Platform.currentNanos() - started
    for (i in result.indices) {
      val pgWrite = pgWrites[start + i]
      val tuple = result[i]
      pgWrite.tuple = tuple
      pgWrite.tupleNumber = tuple.tupleNumber
    }
    if (tuples.size != 1 || pgWrites[start].isFeatureModification) {
      logger.info("INSERT of ${tuples.size} rows took ${elapsed.toDouble() / 1e6}ms")
    }
  }
}
