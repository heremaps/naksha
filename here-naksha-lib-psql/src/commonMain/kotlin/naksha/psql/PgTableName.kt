@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.Id
import naksha.base.IdAttribute
import naksha.base.IdTextType
import naksha.base.Version
import naksha.base.fn.Fn2
import naksha.base.illegalArg
import kotlin.js.JsExport
import kotlin.js.JsStatic

/**
 * A verified collection root name, limited to 29 UTF-8 bytes. The reserved suffix budgets prove that internally generated history/performance partitions and their index names fit PostgreSQL's 63-byte limit; those names need no repeated string validation.
 * @since 3.0
 */
@JsExport
class PgTableName private constructor(id: Id, key: String) : IdAttribute(id, key) {
  /**
   * The verified, unquoted physical collection-root identifier, not necessarily the logical collection identifier in [id]. UNSAFE or over-budget logical text uses an alias such as `t$12345` or `t$_12345`. The 29-byte budget leaves room for generated partition and index suffixes. [checked] preserves its supplied physical spelling.
   * @since 3.0
   */
  val name: String = if (key == PHYSICAL_ATTR) checkedPhysicalName(id.text, PG_ROOT_NAME_BYTES) else alias(id, IdTextType.URL_SAFE, PG_ROOT_NAME_BYTES, "t", PG_ROOT_NAME_BYTES)

  /**
   * Cached SQL-quoted [name], preserving case and escaping embedded double quotes. The containing schema is quoted and supplied separately.
   * @since 3.0
   */
  val quotedName: String = PgUtil.quoteIdent(name)

  /**
   * The unquoted physical HEAD-partition name, formed once as `<root>$head` from the verified root. It is not the collection root or a schema-qualified name.
   * @since 3.0
   */
  val headName: String = "$name\$head"
  override fun toString(): String = name
  override fun equals(other: Any?): Boolean = other is PgTableName && name == other.name
  override fun hashCode(): Int = name.hashCode()
  operator fun plus(suffix: String): String = name + suffix

  /**
   * Generates a finite partition name from verified root bytes and bounded numeric suffixes.
   * @since 3.0
   */
  fun historyName(version: Long, shift: Int, partitionIndex: Int? = null): String {
    if (version < 0 || version >= Version.HEAD.number || shift !in 12..41) throw illegalArg("Invalid finite history version or shift")
    if (partitionIndex != null && partitionIndex !in 0..255) throw illegalArg("Invalid performance partition index")
    return "$name\$${version shr shift}" + if (partitionIndex == null) "" else "\$$partitionIndex"
  }

  companion object {
    /** The stable attribute-map key for logical collection-to-root derivation. */
    private const val ATTR = "naksha.psql.PgTableName"
    /** A distinct key for exact physical names, preventing reuse as logically derived aliases. */
    private const val PHYSICAL_ATTR = "naksha.psql.PgTableName.physical"
    /** Shared key/Id constructor, avoiding a capturing callback allocation on each lookup. */
    private val CREATE = Fn2<PgTableName, String, Id> { key, id -> PgTableName(id, key) }

    /**
     * Returns the canonical cached logical-name attribute.
     * @since 3.0
     */
    @JsStatic
    fun of(id: Id): PgTableName = id.attributes.getOrCreate(ATTR, id, CREATE).ownedBy(id)

    /**
     * Validates an external physical collection root without aliasing it.
     * @since 3.0
     */
    @JsStatic
    fun checked(name: String): PgTableName {
      val id = Id(name)
      return id.attributes.getOrCreate(PHYSICAL_ATTR, id, CREATE).ownedBy(id)
    }
  }
}
