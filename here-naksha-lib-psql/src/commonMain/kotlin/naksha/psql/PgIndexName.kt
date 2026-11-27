@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.Id
import naksha.base.IdAttribute
import naksha.base.IdTextType
import naksha.base.fn.Fn2
import naksha.base.illegalId
import kotlin.js.JsExport
import kotlin.js.JsStatic

/**
 * A verified, cached index alias of at most 13 UTF-8 bytes. Appending it to a verified root or an internally generated partition respects the shared suffix budget by construction.
 * @since 3.0
 */
@JsExport
class PgIndexName private constructor(id: Id, key: String) : IdAttribute(id, key) {
  /**
   * The verified, unquoted index alias of at most 13 UTF-8 bytes, which may differ from the logical index identifier in [id], for example `i$_12345`. This is not a complete table-prefixed index name; [onTable] and [onHistoryTable] generate those identifiers.
   * @since 3.0
   */
  val name: String = alias(id, IdTextType.SAFE, PG_INDEX_ALIAS_BYTES, "i")

  /**
   * Cached SQL-quoted alias [name], without a table prefix. Complete generated index identifiers must be quoted separately when used in SQL text.
   * @since 3.0
   */
  val quotedName: String = PgUtil.quoteIdent(name)
  override fun toString(): String = name
  override fun equals(other: Any?): Boolean = other is PgIndexName && name == other.name
  override fun hashCode(): Int = name.hashCode()

  /**
   * Generates the index name on a verified collection root without rechecking its bytes.
   * @since 3.0
   */
  fun onTable(table: PgTableName): String = "${table.name}\$$name"

  /**
   * Generates an index name on an internally generated history partition.
   * @since 3.0
   */
  fun onHistoryTable(table: PgTableName, version: Long, shift: Int, partitionIndex: Int? = null): String =
    "${table.historyName(version, shift, partitionIndex)}\$$name"

  /**
   * Validates a complete externally supplied table/index combination at its boundary.
   * @since 3.0
   */
  fun onPhysicalTable(table: String): String {
    if (table.isEmpty()) throw illegalId("Physical table name must not be empty")
    return checkedPhysicalName("$table\$$name", PG_IDENTIFIER_BYTES)
  }

  companion object {
    /** The stable attribute-map key for the bounded index alias of this class. */
    private const val ATTR = "naksha.psql.PgIndexName"
    /** Shared key/Id constructor, avoiding a capturing callback allocation on each lookup. */
    private val CREATE = Fn2<PgIndexName, String, Id> { key, id -> PgIndexName(id, key) }

    /**
     * Returns the canonical cached index-name attribute.
     * @since 3.0
     */
    @JsStatic
    fun of(id: Id): PgIndexName = id.attributes.getOrCreate(ATTR, id, CREATE).ownedBy(id)
  }
}
