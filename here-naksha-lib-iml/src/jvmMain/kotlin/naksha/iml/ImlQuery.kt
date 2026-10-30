package naksha.iml

import java.sql.PreparedStatement
import java.util.Collections
import java.util.IdentityHashMap
import naksha.base.Platform
import naksha.base.Version
import naksha.base.illegalArg
import naksha.base.unsupportedOp
import naksha.model.FETCH_ALL
import naksha.model.NO_CACHE_BIT
import naksha.model.objects.XyzMembers
import naksha.model.request.ReadFeatures
import naksha.model.request.ops.And
import naksha.model.request.ops.Equals
import naksha.model.request.ops.Intersects
import naksha.model.request.ops.IsAnyOf
import naksha.model.request.ops.IsNull
import naksha.model.request.ops.Not
import naksha.model.request.ops.Op
import naksha.model.request.ops.Or
import naksha.model.request.ops.QueryConverter
import naksha.model.request.ops.StartsWith
import naksha.model.request.ops.TagListContains
import naksha.model.request.ops.TagListContainsAllOf
import naksha.model.request.ops.TagListContainsAnyOf
import naksha.model.request.query.ISpatialQuery
import naksha.model.request.query.ITagQuery
import naksha.model.request.query.SortOrder
import naksha.model.request.query.SpAnd
import naksha.model.request.query.SpIntersects
import naksha.model.request.query.SpNot
import naksha.model.request.query.SpOr
import naksha.model.request.query.TagAnd
import naksha.model.request.query.TagExists
import naksha.model.request.query.TagNot
import naksha.model.request.query.TagOr
import naksha.model.request.query.TagSetContains

/**
 * Compiles bounded HEAD selection predicates without table names. LIMIT and OFFSET are bound separately by the caller.
 * @since 3.0
 */
@Suppress("DEPRECATION")
internal class ImlQuery(request: ReadFeatures, hardCap: Int) {
  val predicate: String
  val parameters: List<Any?>
  val orderBy: String
  val limit: Int

  private val values = ArrayList<Any?>()
  private val path = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
  private var nodes = 0

  init {
    if (hardCap <= 0) throw illegalArg("IML hardCap must be positive")
    val requestedLimit = request.limit
    if (requestedLimit != null && requestedLimit <= 0) throw illegalArg("IML limit must be positive")
    limit = minOf(requestedLimit ?: hardCap, hardCap)
    if (request.queryHistory || request.minVersion != null || request.versions != 1 ||
      (request.version != null && request.version != Version.HEAD.number)) {
      throw unsupportedOp("IML supports only one HEAD state without a minimum version or history")
    }
    if (request.returnHandle == true) throw unsupportedOp("IML query handles are not supported")
    if (request.resultFilters.isNotEmpty()) throw unsupportedOp("IML result filters are not supported")
    if ((request.fetchMode and NO_CACHE_BIT.inv()) != FETCH_ALL) {
      throw unsupportedOp("IML supports only fetching complete tuples")
    }

    val modern = request.queryMembers
    val legacy = request.query
    val ids = request.featureIds
    val guids = request.guids
    if (modern != null && (ids.isNotEmpty() || guids.isNotEmpty() || !legacy.hasNoConditions())) {
      throw illegalArg("IML queryMembers must not be combined with legacy query options")
    }
    if (guids.isNotEmpty()) throw unsupportedOp("IML legacy GUID queries are not supported; use loadTuples")
    if (legacy.properties != null || legacy.refTiles.isNotEmpty()) {
      throw unsupportedOp("IML property and reference-tile queries are not supported")
    }

    val terms = ArrayList<String>()
    terms.add("next_version = ${parameter(Long.MAX_VALUE)}")
    if (!request.queryDeleted) terms.add("operation != 'D'")
    if (modern != null) {
      terms.add(compile(modern, 0))
    } else {
      val tags = legacy.tags
      if (tags != null) validateTags(tags, 0)
      val spatial = legacy.spatial
      if (spatial != null) validateSpatial(spatial, 0)
      nodes = 0
      val converted = QueryConverter.convert(legacy)
      if (converted != null) terms.add(compile(converted, 0))
      if (ids.isNotEmpty()) terms.add(anyOf("id", ids))
    }
    predicate = terms.joinToString(" AND ")
    parameters = Collections.unmodifiableList(ArrayList(values))

    val ordering = ArrayList<String>()
    var current = request.orderBy
    var count = 0
    var orderedByI = false
    while (current != null) {
      if (++count > 32 || !path.add(current.platformObject())) throw illegalArg("IML order chain is cyclic or too long")
      val direction = when (current.sortOrder) {
        SortOrder.ANY, SortOrder.ASCENDING -> "ASC"
        SortOrder.DESCENDING -> "DESC"
        else -> throw unsupportedOp("Unsupported IML sort order")
      }
      val member = current.member
      val column = when (member) {
        null -> {
          if (current.next != null || current.sortOrder != SortOrder.ANY) {
            throw illegalArg("IML deterministic ordering must not specify a direction or next order")
          }
          "i"
        }
        "id" -> "id"
        "author" -> "author"
        "fn", "tn" -> "i"
        "v", "version" -> "version"
        else -> throw unsupportedOp("Unsupported IML order member '$member'")
      }
      ordering.add("$column $direction")
      if (column == "version") {
        ordering.add("CASE operation WHEN 'I' THEN 0 WHEN 'U' THEN 1 WHEN 'D' THEN 2 ELSE 3 END $direction")
      }
      if (column == "i") orderedByI = true
      current = current.next
    }
    if (!orderedByI) ordering.add("i ASC")
    orderBy = ordering.joinToString(", ")
    path.clear()
  }

  /**
   * Binds only predicate parameters and returns the first unused JDBC index.
   * @since 3.0
   */
  fun bind(statement: PreparedStatement, startIndex: Int = 1): Int {
    if (startIndex < 1 || startIndex > Int.MAX_VALUE - parameters.size) throw illegalArg("Invalid IML bind start index")
    var index = startIndex
    for (value in parameters) statement.setObject(index++, value)
    return index
  }

  private fun parameter(value: Any?): String {
    if (values.size >= 10000) throw illegalArg("IML query exceeds 10000 parameters")
    values.add(value)
    return "?"
  }

  private fun textColumn(member: String?): String = when (member) {
    "id" -> "id"
    "author" -> "author"
    else -> throw unsupportedOp("Unsupported IML text query member '$member'")
  }

  private fun text(value: Any?): String {
    if (value !is String) throw illegalArg("IML ID and author values must be strings")
    return value
  }

  private fun anyOf(column: String, items: Iterable<Any?>): String {
    val placeholders = ArrayList<String>()
    var hasNull = false
    for (item in items) {
      if (item == null) hasNull = true else placeholders.add(parameter(text(item)))
    }
    val membership = if (placeholders.isEmpty()) "FALSE" else "($column IN (${placeholders.joinToString(", ")}) AND $column IS NOT NULL)"
    return if (hasNull) "($membership OR $column IS NULL)" else membership
  }

  private fun tags(member: String?, items: Iterable<Any?>, all: Boolean): String {
    if (member != XyzMembers.XyzTags.name) throw unsupportedOp("Unsupported IML tag query member '$member'")
    val strings = ArrayList<String>()
    for (item in items) {
      if (item !is String) throw illegalArg("IML tag values must be strings")
      strings.add(item)
    }
    if (strings.isEmpty()) return if (all) "TRUE" else "FALSE"
    // Keep containment on the unwrapped expression so a matching JSONB GIN index can be used.
    val source = "jsondata->'properties'->'@ns:com:here:xyz'->'tags'"
    val terms = ArrayList<String>()
    if (all) {
      terms.add("$source @> ${parameter(Platform.toJSON(strings))}::jsonb")
    } else {
      for (item in strings) terms.add("$source @> ${parameter(Platform.toJSON(listOf(item)))}::jsonb")
    }
    // A missing path is SQL NULL; make containment two-valued, including beneath NOT.
    return "((${terms.joinToString(" OR ")}) AND $source IS NOT NULL)"
  }

  private fun compile(raw: Op, depth: Int): String {
    if (depth > 64 || ++nodes > 1024 || !path.add(raw.platformObject())) {
      throw illegalArg("IML query is cyclic, too deep, or too large")
    }
    try {
      val op = Op.detect(raw)
      return when (op) {
        is And, is Or -> {
          if (op.at != null) throw illegalArg("IML logical operations must not specify a member")
          val children = if (op is And) op.children else (op as Or).children
          val terms = ArrayList<String>()
          for (child in children) {
            if (child == null) throw illegalArg("IML logical query contains a null child")
            terms.add(compile(child, depth + 1))
          }
          if (terms.isEmpty()) {
            if (op is And) "TRUE" else "FALSE"
          } else "(${terms.joinToString(if (op is And) " AND " else " OR ")})"
        }
        is Not -> {
          if (op.at != null) throw illegalArg("IML logical operations must not specify a member")
          "(NOT ${compile(op.child, depth + 1)})"
        }
        is Equals -> {
          val column = textColumn(op.at)
          val value = op.value
          if (value == null) "$column IS NULL" else "($column = ${parameter(text(value))} AND $column IS NOT NULL)"
        }
        is IsNull -> "${textColumn(op.at)} IS NULL"
        is IsAnyOf -> anyOf(textColumn(op.at), op.items)
        is TagListContains -> tags(op.at, listOf(op.item), true)
        is TagListContainsAllOf -> tags(op.at, op.items, true)
        is TagListContainsAnyOf -> tags(op.at, op.items, false)
        is StartsWith -> {
          val column = textColumn(op.at)
          val prefix = op.value.replace("!", "!!").replace("%", "!%").replace("_", "!_")
          "($column LIKE ${parameter("$prefix%")} ESCAPE '!' AND $column IS NOT NULL)"
        }
        is Intersects -> {
          if (op.at != "geo") throw unsupportedOp("IML intersections require the geo member")
          if (op.transformers.isNotEmpty()) throw unsupportedOp("IML spatial transformations are not supported")
          "(ST_Intersects(geo, ST_SetSRID(ST_GeomFromGeoJSON(${parameter(Platform.toJSON(op.value))}),4326)) AND geo IS NOT NULL)"
        }
        else -> throw unsupportedOp("Unsupported IML query operation '${op.op}'")
      }
    } finally {
      path.remove(raw.platformObject())
    }
  }

  // QueryConverter drops null children and recursively visits legacy tags, so validate first.
  private fun validateTags(query: ITagQuery, depth: Int) {
    if (depth > 64 || ++nodes > 1024 || !path.add(query)) throw illegalArg("IML legacy tag query is cyclic, too deep, or too large")
    try {
      when (query) {
        is TagAnd, is TagOr -> {
          val children: Iterable<ITagQuery?> = if (query is TagAnd) query else query as TagOr
          for (child in children) {
            if (child == null) throw illegalArg("IML legacy tag query contains a null child")
            validateTags(child, depth + 1)
          }
        }
        is TagNot -> validateTags(query.query, depth + 1)
        is TagExists -> Unit
        is TagSetContains -> if (query.element !is String) throw illegalArg("IML tag values must be strings")
        else -> throw unsupportedOp("Unsupported IML legacy tag query")
      }
    } finally {
      path.remove(query)
    }
  }

  // QueryConverter recursively visits legacy queries, so validate before entering it.
  private fun validateSpatial(query: ISpatialQuery, depth: Int) {
    if (depth > 64 || ++nodes > 1024 || !path.add(query)) throw illegalArg("IML legacy spatial query is cyclic, too deep, or too large")
    try {
      when (query) {
        is SpIntersects -> if (query.transformation != null) throw unsupportedOp("IML spatial transformations are not supported")
        is SpAnd, is SpOr -> {
          val children: Iterable<ISpatialQuery?> = if (query is SpAnd) query else query as SpOr
          for (child in children) {
            if (child == null) throw illegalArg("IML legacy spatial query contains a null child")
            validateSpatial(child, depth + 1)
          }
        }
        is SpNot -> validateSpatial(query.query, depth + 1)
        else -> throw unsupportedOp("Unsupported IML legacy spatial query")
      }
    } finally {
      path.remove(query)
    }
  }
}
