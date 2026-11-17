@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.Id
import naksha.base.IdAttribute
import naksha.base.IdTextType
import naksha.base.fn.Fn2
import kotlin.js.JsExport
import kotlin.js.JsStatic

/**
 * A verified schema name. Logical catalogs preserve URL-safe text within 25 bytes and reserved built-ins within 32 bytes. Arbitrary physical schema names are validated once at their input boundary.
 * @since 3.0
 */
@JsExport
class PgSchemaName private constructor(id: Id, key: String) : IdAttribute(id, key) {
  /**
   * The verified, unquoted physical schema identifier, not necessarily the logical catalog identifier in [id]. UNSAFE or over-budget logical text uses an alias such as `s$_12345`; accepted URL-safe text and reserved built-ins retain their spelling within their respective budgets. [checked] preserves its supplied physical spelling.
   * @since 3.0
   */
  val name: String = if (key == PHYSICAL_ATTR) checkedPhysicalName(id.text, PG_IDENTIFIER_BYTES) else alias(id, IdTextType.URL_SAFE, 25, "s", 32)

  /**
   * Cached SQL-quoted [name], preserving case and escaping embedded double quotes. This is one schema identifier, not a qualified relation.
   * @since 3.0
   */
  val quotedName: String = PgUtil.quoteIdent(name)
  override fun toString(): String = name
  override fun equals(other: Any?): Boolean = other is PgSchemaName && name == other.name
  override fun hashCode(): Int = name.hashCode()

  companion object {
    /** The stable attribute-map key for logical catalog-to-schema derivation. */
    private const val ATTR = "naksha.psql.PgSchemaName"
    /** A distinct key for exact physical names, preventing reuse as logically derived aliases. */
    private const val PHYSICAL_ATTR = "naksha.psql.PgSchemaName.physical"
    /** Shared key/Id constructor, avoiding a capturing callback allocation on each lookup. */
    private val CREATE = Fn2<PgSchemaName, String, Id> { key, id -> PgSchemaName(id, key) }

    /**
     * Returns the canonical cached logical-name attribute.
     * @since 3.0
     */
    @JsStatic
    fun of(id: Id): PgSchemaName = id.attributes.getOrCreate(ATTR, id, CREATE).ownedBy(id)

    /**
     * Validates an external physical schema name without changing its spelling.
     * @since 3.0
     */
    @JsStatic
    fun checked(name: String): PgSchemaName {
      val id = Id(name)
      return id.attributes.getOrCreate(PHYSICAL_ATTR, id, CREATE).ownedBy(id)
    }
  }
}
