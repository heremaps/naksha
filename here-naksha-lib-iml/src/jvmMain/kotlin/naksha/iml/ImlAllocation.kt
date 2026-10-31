package naksha.iml

import java.sql.Connection
import naksha.base.NakshaError
import naksha.base.NakshaException

/** Session-owned allocation state, valid only while the transaction's global writer lock is held. @since 3.0 */
internal class ImlAllocation {
  private val tables = HashMap<Pair<String, String>, Long?>()

  /** Returns null for server allocation, otherwise the next legacy row number. @since 3.0 */
  fun next(connection: Connection, layer: ImlMetadata.Layer): Long? {
    val key = layer.schema to layer.table
    if (!tables.containsKey(key)) {
      var default: String? = null
      val identity = connection.prepareStatement("SELECT is_identity, column_default FROM information_schema.columns WHERE table_schema = ? AND table_name = ? AND column_name = 'i'").use {
        it.setString(1, layer.schema); it.setString(2, layer.table)
        it.executeQuery().use { rows ->
          check(rows.next()) { "IML table has no i column" }
          default = rows.getString(2)?.trim()
          rows.getString(1) == "YES"
        }
      }
      val maximum = connection.createStatement().use {
        it.executeQuery("SELECT COALESCE(max(i),0) FROM ${layer.sqlTable}").use { rows -> check(rows.next()); rows.getLong(1) }
      }
      val server = identity || default?.startsWith("nextval(") == true
      if (server) {
        val owned = connection.prepareStatement("SELECT pg_get_serial_sequence(?, 'i')").use {
          it.setString(1, layer.sqlTable)
          it.executeQuery().use { rows -> check(rows.next()); rows.getString(1) }
        }
        // Manually assigned nextval defaults need not own their sequence. Accept only the canonical literal form, never interpolate the default SQL.
        val sequence = if (identity) owned else default?.let {
          Regex("nextval\\('((?:[^']|'')*)'::regclass\\)").matchEntire(it)?.groupValues?.get(1)?.replace("''", "'")
        }
        if (sequence == null) throw NakshaException(NakshaError.CONFLICT, "Cannot resolve IML row allocation sequence")
        val sqlSequence = connection.prepareStatement("SELECT n.nspname, c.relname, s.seqincrement, s.seqcycle FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace JOIN pg_sequence s ON s.seqrelid = c.oid WHERE c.oid = ?::regclass").use {
          it.setString(1, sequence)
          it.executeQuery().use { rows ->
            if (!rows.next() || rows.getLong(3) <= 0 || rows.getBoolean(4)) throw NakshaException(NakshaError.CONFLICT, "IML requires a non-cycling ascending allocation sequence")
            "${ImlMetadata.quote(rows.getString(1))}.${ImlMetadata.quote(rows.getString(2))}"
          }
        }
        val behind = connection.createStatement().use {
          it.executeQuery("SELECT last_value, is_called FROM $sqlSequence").use { rows ->
            check(rows.next())
            val last = rows.getLong(1)
            last < maximum || (last == maximum && !rows.getBoolean(2))
          }
        }
        // Sequence consumers must cooperate with the global writer gate; setval cannot synchronize unrelated external nextval calls.
        if (behind) connection.prepareStatement("SELECT setval(?::regclass, ?, true)").use {
          it.setString(1, sqlSequence); it.setLong(2, maximum); it.executeQuery().close()
        }
      }
      tables[key] = if (server) null else maximum
    }
    val maximum = tables[key] ?: return null
    val next = Math.addExact(maximum, 1L)
    tables[key] = next
    return next
  }

  /** Clears strategies at transaction boundaries and after administrative metadata changes. @since 3.0 */
  fun clear() { tables.clear() }

  /** Captures strategies before a request savepoint, including tables that DDL might replace. @since 3.0 */
  fun checkpoint(): Map<Pair<String, String>, Long?> = HashMap(tables)

  /** Restores strategies after the matching request savepoint was rolled back. @since 3.0 */
  fun restore(checkpoint: Map<Pair<String, String>, Long?>) {
    tables.clear()
    tables.putAll(checkpoint)
  }
}
