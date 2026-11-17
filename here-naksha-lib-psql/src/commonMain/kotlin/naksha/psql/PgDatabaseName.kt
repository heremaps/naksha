@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.Id
import naksha.base.IdAttribute
import naksha.base.IdTextType
import naksha.base.fn.Fn2
import kotlin.js.JsExport
import kotlin.js.JsStatic

/**
 * A verified PostgreSQL database name. Logical identifiers use the existing 42-byte UNSAFE/dollar-alias policy; checked physical input is preserved exactly.
 * @since 3.0
 */
@JsExport
class PgDatabaseName private constructor(id: Id, key: String) : IdAttribute(id, key) {
  /**
   * The verified, unquoted physical database identifier, which may differ from the logical identifier in [id]. Logical text outside the accepted classification or 42-byte budget uses an alias such as `d$_12345`; ordinary UNSAFE text within that budget is preserved. [checked] preserves its supplied physical spelling.
   * @since 3.0
   */
  val name: String = if (key == PHYSICAL_ATTR) checkedPhysicalName(id.text, PG_IDENTIFIER_BYTES) else alias(id, IdTextType.UNSAFE, 42, "d")

  /**
   * Cached SQL-quoted [name], preserving case and escaping embedded double quotes. This represents one identifier, not a connection URI component.
   * @since 3.0
   */
  val quotedName: String = PgUtil.quoteIdent(name)
  override fun toString(): String = name
  override fun equals(other: Any?): Boolean = other is PgDatabaseName && name == other.name
  override fun hashCode(): Int = name.hashCode()

  companion object {
    /** The stable attribute-map key for logical database-name derivation. */
    private const val ATTR = "naksha.psql.PgDatabaseName"
    /** A distinct key for exact physical names, preventing reuse as logically derived aliases. */
    private const val PHYSICAL_ATTR = "naksha.psql.PgDatabaseName.physical"
    /** Shared key/Id constructor, avoiding a capturing callback allocation on each lookup. */
    private val CREATE = Fn2<PgDatabaseName, String, Id> { key, id -> PgDatabaseName(id, key) }

    /**
     * Returns the canonical cached logical-name attribute.
     * @since 3.0
     */
    @JsStatic
    fun of(id: Id): PgDatabaseName = id.attributes.getOrCreate(ATTR, id, CREATE).ownedBy(id)

    /**
     * Validates an external physical database name without aliasing it.
     * @since 3.0
     */
    @JsStatic
    fun checked(name: String): PgDatabaseName {
      val id = Id(name)
      return id.attributes.getOrCreate(PHYSICAL_ATTR, id, CREATE).ownedBy(id)
    }
  }
}
