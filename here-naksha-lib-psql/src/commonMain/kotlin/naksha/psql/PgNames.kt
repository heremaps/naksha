package naksha.psql

import naksha.base.Id
import naksha.base.IdAttribute
import naksha.base.IdTextType
import naksha.base.illegalId
import naksha.base.illegalState

/**
 * Converts a verified database, schema, table, or index name into its unquoted physical string for SQL value binding. Null and ordinary data values are returned unchanged. This does not quote identifiers for insertion into SQL text; name classes provide their cached quoted forms for that purpose.
 * @since 3.0
 */
internal fun pgNameArgument(value: Any?): Any? = when (value) {
  is PgDatabaseName -> value.name
  is PgSchemaName -> value.name
  is PgTableName -> value.name
  is PgIndexName -> value.name
  else -> value
}

/**
 * Applies [pgNameArgument] to SQL parameters without mutating the caller's array. Returns the original array when no names need unwrapping; otherwise makes one shallow copy and replaces only the verified-name values. This preserves ordinary values, including binary data and numeric types.
 * @since 3.0
 */
internal fun pgNameArguments(args: Array<Any?>): Array<Any?> {
  var encoded: Array<Any?>? = null
  for (i in args.indices) {
    val value = args[i]
    val name = pgNameArgument(value)
    if (name !== value) {
      if (encoded == null) encoded = args.copyOf()
      encoded[i] = name
    }
  }
  return encoded ?: args
}

/**
 * Returns this cached attribute after verifying that it belongs to the exact Id instance requesting it, not merely an equal identifier. AttributeMap permits direct mutation, so a foreign IdAttribute could otherwise be installed under a valid cache key and return another identifier's derived name.
 * @throws naksha.base.NakshaException with an illegal-state error when the owner differs.
 * @since 3.0
 */
internal fun <T : IdAttribute> T.ownedBy(id: Id): T {
  if (this.id !== id) throw illegalState("Identifier attribute '$key' belongs to another Id instance")
  return this
}

/**
 * Preserves legal identifier text when [expected] accepts its classification and it fits the UTF-8 [bytes] budget. Reserved names not accepted by [expected] may instead use [reservedBytes]; zero disables that exception. Other legal text becomes an internal alias consisting of [prefix], a dollar sign, and the Id's decimal intValue, with a leading minus replaced by an underscore. Illegal text is rejected rather than aliased. Callers supply fixed prefixes and budgets that guarantee the generated alias fits; this performs no SQL quoting or database collision checks.
 * @throws naksha.base.NakshaException with an illegal-identifier error when the logical text is illegal.
 * @since 3.0
 */
internal fun alias(id: Id, expected: IdTextType, bytes: Int, prefix: String, reservedBytes: Int = 0): String {
  val text = id.text
  val type = id.textType
  if (type === IdTextType.ILLEGAL) throw illegalId("Illegal identifier text")
  val limit = if (expected.accepts(type)) bytes else if (type === IdTextType.RESERVED) reservedBytes else 0
  if (limit > 0 && text.encodeToByteArray().size <= limit) return text
  val number = id.intValue
  return "$prefix\$" + if (number < 0) "_${-number.toLong()}" else number.toString()
}

/**
 * Validates an external physical identifier at its input boundary: it must be nonempty, contain no NUL, be valid Unicode, and occupy at most [maxBytes] UTF-8 bytes. Returns the original spelling unchanged, without applying logical-name classification, aliasing, case folding, or SQL quoting. Characters such as quotes and spaces remain valid because physical identifiers are quoted separately when used in SQL text.
 * @throws naksha.base.NakshaException with an illegal-identifier error when validation fails.
 * @since 3.0
 */
internal fun checkedPhysicalName(name: String, maxBytes: Int): String {
  if (name.isEmpty() || '\u0000' in name) throw illegalId("Physical identifier must contain 1..$maxBytes UTF-8 bytes without NUL")
  val bytes = try {
    name.encodeToByteArray(throwOnInvalidSequence = true).size
  } catch (_: CharacterCodingException) {
    throw illegalId("Physical identifier must be valid Unicode")
  }
  if (bytes > maxBytes) throw illegalId("Physical identifier exceeds $maxBytes UTF-8 bytes")
  return name
}
