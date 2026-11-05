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
 * The writer resolves target features and expected states and assigns returned final states; SQL, locking, tombstoning, archival and conflict handling live in [PgDatabase]. Contiguous atomic/non-atomic runs preserve ID lock order. PURGE removes only existing DELETE HEAD states.
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
   * Persist the selected deletes or purges in ID order and assign their returned states.
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
      val results = database.rows_purge(conn, pgCollection, version, featureIds)
      for (i in results.indices) {
        // PURGE returns unchanged live HEADs too; only a returned tombstone was actually purged.
        pgWrites[start + i].changed = results[i]?.tupleNumber?.isTombstone == true
      }
    } else {
      val databaseNumber = session.storage.id.number
      val catalogNumber = pgCollection.catalogId.intValue
      val collectionNumber = pgCollection.id.intValue
      var s = start
      while (s < end) {
        val isAtomic = pgWrites[s].requireVersion != null
        var e = s + 1
        while (e < end && (pgWrites[e].requireVersion != null) == isAtomic) e++
        val results: List<NewTuple>
        if (isAtomic) {
          val guids = ArrayList<Guid>(e - s)
          for (i in s until e) {
            val write = pgWrites[i]
            guids.add(Guid(write.id.text, TupleNumber(databaseNumber, catalogNumber, collectionNumber, write.id.number, write.requireVersion!!)))
          }
          results = database.rows_delete_by_guid_with_result(conn, pgCollection, version, guids)
        } else {
          val ids = ArrayList<Id>(e - s)
          for (i in s until e) ids.add(pgWrites[i].id)
          results = database.rows_delete_by_id_with_result(conn, pgCollection, version, ids)
        }
        val byFn = HashMap<Long, NewTuple>(results.size)
        for (result in results) byFn[result.tuple.tupleNumber.featureNumber] = result
        for (i in s until e) {
          val write = pgWrites[i]
          val result = byFn[write.id.number] ?: continue
          val tuple = result.tuple
          write.changed = result.previousTupleNumber != null
          write.tuple = tuple
          write.tupleNumber = tuple.tupleNumber
          write.action = Action.DELETE
        }
        s = e
      }
    }
    val elapsed = Platform.currentNanos() - started
    if ((end - start) != 1 || pgWrites[start].isFeatureModification) {
      logger.info("${if (purge) "PURGE" else "DELETE"} of ${end - start} rows took ${elapsed.toDouble() / 1e6}ms")
    }
  }
}
