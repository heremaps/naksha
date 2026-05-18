package naksha.psql

import naksha.base.Platform
import naksha.base.Platform.PlatformCompanion.logger
import naksha.base.PlatformUtil
import naksha.psql.PgColumn.PgColumnCompanion.allColumns

/**
 * Execute an **INSERT** _(aka [CREATE][naksha.model.request.WriteOp.CREATE])_ into a collection.
 * @since 3.0
 * @see [PgWriter]
 */
internal class PgWriterInsert(writer: PgWriter, collection: PgCollection, partition: Int, writes: List<PgWrite>)
    : PgWriterBase(writer, collection, partition, writes)
{
    init {
        inRows.addColumns(allColumns)
        val members = collection.head.members
        inRows.addCustomMembers(members)
        var i = 0
        for (write in writes) {
            val tuple = write.tuple
            if (tuple != null) {
                inRows[i] = tuple
                inRows.setCustomMembers(i, write.feature, members)
                i++
            }
        }
    }

    private fun plan(conn: PgConnection, collection: PgCollection): PgWriterPlan {
        val new_row = """WITH new_row AS (
  SELECT * FROM UNNEST(${inRows.placeholders()}) AS t(${inRows.names()})
)"""

        // If the shadow table exists, delete old states
        val clear_shadow = if (shadowTable != null) """, clear_shadow AS (
  DELETE FROM ${shadowTable.quotedName}
  WHERE id IN (SELECT id FROM new_row)
  RETURNING id, tn
)""" else ""

        // Insert the features
        val inserted = """, inserted AS (
INSERT INTO ${headTable.quotedName} (${inRows.names()})
SELECT * FROM new_row
RETURNING id, tn
)"""

        // Actually perform the insert.
        val SQL = """$new_row$clear_shadow$inserted
SELECT inserted.id AS id, inserted.tn AS tn${if (clear_shadow.isNotEmpty()) ", clear_shadow.tn AS clear_tn" else ""}
FROM inserted
${if (clear_shadow.isNotEmpty()) "LEFT JOIN clear_shadow ON clear_shadow.id = inserted.id" else ""} 
"""
        val typeNames = inRows.typeNames()
        val pgPlan = conn.prepare(SQL, typeNames)
        return PgWriterPlan(pgPlan, SQL, typeNames)
    }

    override fun doExecute(conn: PgConnection) {
        if (writes.isEmpty()) return
        val plan = plan(conn, collection)
        val array = inRows.values()
        if (PlatformUtil.ENABLE_INFO) {
            if (session.logQueries) {
                session.logAtInfo(plan.sql)
            }
            if (session.logExplain) {
                val explain = session.explain(conn, false, plan.sql, plan.typeNames, array)
                session.logAtInfo(explain)
            }
        }
        val start = Platform.currentNanos()
        // We ignore the result, we know that if it didn't fail, it's okay.
        plan.pgPlan.execute(array).close()
        val end = Platform.currentNanos()
        val seconds = (end.toDouble() - start.toDouble()) / 1e9
        if (writes.size != 1 || writes[0].isFeatureModification) {
            logger.info("INSERT of ${inRows.size} rows took ${seconds * 1000}ms, therefore ${inRows.size / seconds} features/s, partitions: $featureCountByPartitionJoined")
        }
    }
}