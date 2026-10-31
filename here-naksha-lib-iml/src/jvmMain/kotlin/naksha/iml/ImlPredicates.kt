package naksha.iml

import naksha.base.illegalArg
import naksha.base.unsupportedOp
import naksha.model.objects.Member
import naksha.model.objects.MemberList
import naksha.model.objects.MemberType
import naksha.model.objects.XyzMembers

/** Resolves canonical IML fields and validated collection metadata, never request-supplied SQL paths. @since 3.0 */
internal class ImlPredicates(private val parameter: (Any?) -> String, members: MemberList? = null) {
  private val xyz = "jsondata->'properties'->'@ns:com:here:xyz'"
  private val resolved = ImlTuple.effectiveMembers(members)

  private fun custom(member: String?): Member? {
    if (member in listOf("fn", "version", "action")) return null
    if (member in listOf("pn", "pt", "gv") && resolved[member] == null) return null
    val declared = resolved[member] ?: throw unsupportedOp("Unsupported IML query member '$member'")
    for (canonical in XyzMembers.ALL) if (canonical.name == member) return null
    return declared
  }

  private fun json(member: Member): String {
    val keys = ArrayList<String>()
    for (segment in member.path) {
      val key = segment.toString().replace("\\", "\\\\").replace("'", "''")
      keys.add("E'$key'")
    }
    // Metadata literals have no placeholders, so reusing an expression cannot reorder bindings.
    return "(jsondata #> ARRAY[${keys.joinToString(", ")}]::text[])"
  }

  fun geometry(member: String?): String {
    val declared = custom(member)
    if (declared == null) {
      if (member == "geo") return "geo"
      throw unsupportedOp("Unsupported IML spatial query member '$member'")
    }
    if (declared.isVirtual() || declared.dataType != MemberType.SPATIAL) throw illegalArg("IML member '$member' is not spatial")
    val json = json(declared)
    return "(CASE WHEN jsonb_typeof($json) = 'object' THEN ST_SetSRID(ST_GeomFromGeoJSON(($json)::text),4326) END)"
  }

  fun column(member: String?, kind: String? = null): String {
    val custom = custom(member)
    if (custom != null) {
      if (custom.isVirtual()) throw unsupportedOp("Unsupported IML virtual member '$member'")
      val declared = when (custom.dataType) {
        MemberType.STRING -> "string"
        MemberType.BOOLEAN -> "boolean"
        MemberType.INT8, MemberType.INT16, MemberType.INT32, MemberType.INT64,
        MemberType.FLOAT32, MemberType.FLOAT64 -> "number"
        else -> throw unsupportedOp("Unsupported IML scalar member type '${custom.dataType}'")
      }
      if (kind != null && kind != declared) {
        if (kind == "boolean") return "NULL::boolean"
        throw illegalArg("IML member '$member' requires $declared values")
      }
      val json = json(custom)
      val text = "($json #>> ARRAY[]::text[])"
      val value = when (declared) {
        "boolean" -> "$text::boolean"
        "number" -> "$text::numeric"
        else -> text
      }
      return "(CASE WHEN jsonb_typeof($json) = '$declared' THEN $value END)"
    }
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
        if (resolved[member]?.dataType != MemberType.BOOLEAN) {
          column(member)
          throw illegalArg("IML member '$member' is not a declared boolean scalar")
        }
        "boolean"
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
