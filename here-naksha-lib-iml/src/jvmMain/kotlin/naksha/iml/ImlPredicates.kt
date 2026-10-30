package naksha.iml

import naksha.base.illegalArg
import naksha.base.unsupportedOp

/** Fixed IML member resolution; request-supplied paths and identifiers never enter SQL. @since 3.0 */
internal class ImlPredicates(private val parameter: (Any?) -> String) {
  private val xyz = "jsondata->'properties'->'@ns:com:here:xyz'"

  fun column(member: String?, kind: String? = null): String {
    val direct = when (member) {
      "id" -> "id"
      "author" -> "author"
      "fn" -> "i"
      "version" -> "(CASE WHEN version = 9223372036854775807 THEN 9007199254740991::numeric ELSE version::numeric * 4 + CASE operation WHEN 'I' THEN 0 WHEN 'U' THEN 1 WHEN 'D' THEN 2 ELSE 3 END END)"
      "nv" -> "(CASE WHEN next_version = 9223372036854775807 THEN 9007199254740991::numeric ELSE next_version::numeric * 4 + 3 END)"
      "action" -> "CASE operation WHEN 'I' THEN 0 WHEN 'U' THEN 1 WHEN 'D' THEN 2 ELSE 3 END"
      "gbfn" -> "NULL::numeric"
      else -> null
    }
    if (direct != null) {
      if (kind == "boolean") return "NULL::boolean"
      if (kind == "string" && member !in listOf("id", "author")) throw illegalArg("IML member '$member' is numeric")
      if (kind == "number" && member in listOf("id", "author")) throw illegalArg("IML member '$member' is text")
      return direct
    }
    val field = when (member) {
      "updated_at" -> "updatedAt"
      "created_at" -> "createdAt"
      "author_ts" -> "authorTs"
      "hash" -> "hash"
      "here_tile" -> "hereTile"
      "cc" -> "changeCount"
      "app_id" -> "appId"
      "origin" -> "origin"
      "target" -> "target"
      "cv0", "cv1", "cv2", "cv3", "cs0", "cs1", "cs2", "cs3" -> member
      "ft" -> "featureType"
      "pn", "pt", "gv" -> member
      else -> throw unsupportedOp("Unsupported IML scalar query member '$member'")
    }
    val source = if (member in listOf("ft", "pn", "pt", "gv")) "jsondata->'properties'" else xyz
    val json = "$source->'$field'"
    val declared = if (member in listOf("app_id", "origin", "target", "ft", "cs0", "cs1", "cs2", "cs3")) "string" else "number"
    if (kind != null && kind != declared) {
      if (kind == "boolean") return "NULL::boolean"
      throw illegalArg("IML member '$member' requires $declared values")
    }
    val type = declared
    return when (type) {
      "string" -> "(CASE WHEN jsonb_typeof($json) = 'string' THEN $source->>'$field' END)"
      "boolean" -> "(CASE WHEN jsonb_typeof($json) = 'boolean' THEN ($source->>'$field')::boolean END)"
      else -> "(CASE WHEN jsonb_typeof($json) = 'number' THEN ($source->>'$field')::numeric END)"
    }
  }

  fun compare(member: String?, value: Any?, operator: String): String {
    if (value == null) {
      if (operator != "=") throw illegalArg("IML range values must not be null")
      return "${column(member)} IS NULL"
    }
    val kind = when (value) {
      is String -> "string"
      is Boolean -> {
        column(member)
        throw illegalArg("IML has no declared boolean scalar members; use IsTrue or IsFalse for type tests")
      }
      is Number -> {
        if (!value.toDouble().isFinite()) throw illegalArg("IML numeric values must be finite")
        "number"
      }
      else -> throw illegalArg("IML scalar values must be strings, numbers, booleans or null")
    }
    if (value is Boolean && operator != "=") throw illegalArg("IML boolean ranges are not supported")
    val column = column(member, kind)
    return "($column $operator ${parameter(value)} AND $column IS NOT NULL)"
  }

  fun anyOf(member: String?, items: Iterable<Any?>): String {
    val terms = ArrayList<String>()
    for (item in items) terms.add(compare(member, item, "="))
    if (terms.isEmpty()) {
      column(member)
      return "FALSE"
    }
    return "(${terms.joinToString(" OR ")})"
  }

  fun startsWith(member: String?, value: String): String {
    val column = column(member, "string")
    val prefix = value.replace("!", "!!").replace("%", "!%").replace("_", "!_")
    return "($column LIKE ${parameter("$prefix%")} ESCAPE '!' AND $column IS NOT NULL)"
  }
}
