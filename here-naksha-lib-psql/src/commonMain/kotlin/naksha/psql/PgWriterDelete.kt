package naksha.psql

import naksha.base.Action
import naksha.base.Guid
import naksha.base.Id
import naksha.base.Platform
import naksha.base.Platform.PlatformCompanion.logger
import naksha.base.TupleNumber
import naksha.model.NewTuple

/**
 * Execute a [DELETE][naksha.model.request.WriteOp.DELETE] or PURGE through [PgDatabase].
 *
 * The writer only resolves the target features (and, for atomic deletes, their expected states) and
 * assigns the returned final states; all SQL, locking, tombstoning, archival and conflict handling
 * live in [PgDatabase]. Non-atomic deletes are keyed by feature id, atomic deletes by their expected
 * GUID, and PURGE removes only existing DELETE HEAD states.
 * @since 3.0
 * @see [PgWriter]
 */
internal class PgWriterDelete(
  private val pgWriter: PgWriter,
  private val pgCollection: PgCollection,
  private val pgWrites: List<PgWrite>,
  private val start: Int,
  private val end: Int,
  private val purge: Boolean = false
) {
  /**
   * Persist the selected deletes or purges and assign their returned states in input order.
   * @since 3.0
   */
  fun execute(conn: PgConnection) {
    if (start == end) return
    val session = pgWriter.session
    pgWrites[start].dbCatalog.applySearchPath(conn)
    // TODO(db-08 logging): wire session.logQueries/logExplain through PgDatabase diagnostics.
    val version = session.useTransaction().version.number
    val database = session.storage.database
    val started = Platform.currentNanos()
    if (purge) {
      val featureIds = ArrayList<Id>(end - start)
      for (i in start ..< end) featureIds.add(pgWrites[i].id)
      database.rows_purge(conn, pgCollection, version, featureIds)
    } else {
      val databaseNumber = session.storage.id.number
      val catalogNumber = pgCollection.catalogId.intValue
      val collectionNumber = pgCollection.id.intValue
      val nonAtomic = ArrayList<Id>()
      val atomic = ArrayList<Guid>()
      for (i in start ..< end) {
        val pgWrite = pgWrites[i]
        val requireVersion = pgWrite.requireVersion
        if (requireVersion == null) {
          nonAtomic.add(pgWrite.id)
        } else {
          atomic.add(Guid(pgWrite.id.text, TupleNumber(databaseNumber, catalogNumber, collectionNumber, pgWrite.id.number, requireVersion)))
        }
      }
      val results = ArrayList<NewTuple>(end - start)
      if (nonAtomic.isNotEmpty()) results.addAll(database.rows_delete_by_id_with_result(conn, pgCollection, version, nonAtomic))
      if (atomic.isNotEmpty()) results.addAll(database.rows_delete_by_guid_with_result(conn, pgCollection, version, atomic))
      val byFn = HashMap<Long, NewTuple>()
      for (result in results) byFn[result.tuple.tupleNumber.featureNumber] = result
      for (i in start ..< end) {
        val pgWrite = pgWrites[i]
        val result = byFn[pgWrite.id.number]
        if (result != null) {
          pgWrite.tuple = result.tuple
          pgWrite.tupleNumber = result.tuple.tupleNumber
          pgWrite.action = Action.DELETE
        }
      }
    }
    val elapsed = Platform.currentNanos() - started
    if ((end - start) != 1 || pgWrites[start].isFeatureModification) {
      logger.info("${if (purge) "PURGE" else "DELETE"} of ${end - start} rows took ${elapsed.toDouble() / 1e6}ms")
    }
  }
}
