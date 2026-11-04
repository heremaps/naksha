package naksha.psql

import naksha.base.Action
import naksha.base.NakshaError
import naksha.base.NakshaException
import naksha.base.Platform
import naksha.base.Platform.PlatformCompanion.logger
import naksha.base.TupleNumber
import naksha.base.catalogNotFound
import naksha.base.collectionNotFound
import naksha.base.illegalArg
import naksha.model.NewTuple

/**
 * Execute a [UPDATE][naksha.model.request.WriteOp.UPDATE] through [PgDatabase].
 *
 * The writer only assembles the immutable [NewTuple] inputs (with their expected predecessors)
 * and assigns the returned final states; all SQL, locking, archival and conflict handling live in
 * [PgDatabase.rows_update].
 * @since 3.0
 * @see [PgWriter]
 */
internal class PgWriterUpdate(
  private val pgWriter: PgWriter,
  private val pgCollection: PgCollection,
  private val pgWrites: List<PgWrite>,
  private val start: Int,
  private val end: Int
) {
  /**
   * Persist the selected updates and assign their returned states in input order.
   * @since 3.0
   */
  fun execute(conn: PgConnection) {
    if (start == end) return
    val tuples = ArrayList<NewTuple>(end - start)
    for (i in start ..< end) {
      val pgWrite = pgWrites[i]
      val tuple = pgWrite.tuple ?: throw illegalArg("The write #$i has no tuple")
      val previous = pgWrite.requireVersion?.let { TupleNumber.copy(tuple.tupleNumber, version = it) }
      tuples.add(NewTuple(tuple, previous))
    }
    val session = pgWriter.session
    pgWrites[start].dbCatalog.applySearchPath(conn)
    // TODO(db-07 logging): wire session.logQueries/logExplain through PgDatabase diagnostics.
    val started = Platform.currentNanos()
    val result = try {
      session.storage.database.rows_update(
        conn, pgCollection.catalogId, pgCollection.id, tuples, pgCollection.storeHistory, pgCollection.columns
      )
    } catch (e: NakshaException) {
      // A missing HEAD for a metadata modification is a missing catalog/collection, not a missing feature.
      val first = pgWrites[start]
      if (e.error.code == NakshaError.FEATURE_NOT_FOUND && first.isCollectionModification) {
        throw collectionNotFound("Collection '${first.id.text}' does not exist in catalog '${first.dbCatalog.id.text}'")
      }
      if (e.error.code == NakshaError.FEATURE_NOT_FOUND && first.isCatalogModification) {
        throw catalogNotFound("Catalog '${first.id.text}' does not exist")
      }
      throw e
    }
    val elapsed = Platform.currentNanos() - started
    for (i in result.indices) {
      val pgWrite = pgWrites[start + i]
      val newTuple = result[i]
      pgWrite.tuple = newTuple.tuple
      pgWrite.tupleNumber = newTuple.tuple.tupleNumber
      pgWrite.action = Action.UPDATE
    }
    if (tuples.size != 1 || pgWrites[start].isFeatureModification) {
      logger.info("UPDATE of ${tuples.size} rows took ${elapsed.toDouble() / 1e6}ms")
    }
  }
}
