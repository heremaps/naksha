package naksha.iml

import java.util.Collections
import naksha.base.Version
import naksha.base.illegalArg
import naksha.model.request.ReadFeatures

/**
 * Compiles temporal selection over IML source sequences, not packed Naksha versions. Bounds accept manual versions, zero, or HEAD; action bits do not narrow a sequence boundary.
 *
 * Without history, bounds only filter current HEAD rows. With history and one version, selects the state whose validity interval contains the upper bound, optionally restricting its source version by the lower bound. With multiple versions, ranks candidates matching both the version window and caller filters by source version, actual action (D, U, I), then row number, all descending.
 *
 * Unless queryDeleted is set, single-state queries exclude tombstones. Multi-state queries suppress an entire ID when its newest matching candidate is deleted, and otherwise omit older tombstones without refilling their rank slots. Request properties retain their ReadFeatures normalization semantics.
 * @since 3.0
 */
internal class ImlHistory(request: ReadFeatures) {
  val predicate: String
  val parameters: List<Any?>
  val versions: Int = request.versions
  val historical: Boolean = request.queryHistory
  val snapshot: Boolean = historical && versions == 1

  private val queryDeleted = request.queryDeleted

  init {
    if (versions < 1) throw illegalArg("IML versions must be positive")
    if (!historical && versions > 1) throw illegalArg("IML multiple versions require queryHistory")
    val minimum = request.minVersion
    val maximum = request.version ?: Version.HEAD.number
    for (bound in listOfNotNull(minimum, maximum)) {
      val version = Version(bound)
      if (bound != 0L && bound != Version.HEAD.number && !version.isManual()) {
        throw illegalArg("IML version bounds must be manual versions or HEAD")
      }
    }
    val minimumSequence = minimum?.let { if (it == Version.HEAD.number) Long.MAX_VALUE else it ushr 2 }
    val maximumSequence = if (maximum == Version.HEAD.number) Long.MAX_VALUE else maximum ushr 2
    if (minimumSequence != null && minimumSequence > maximumSequence) throw illegalArg("IML minimum version exceeds maximum version")

    val terms = ArrayList<String>()
    val values = ArrayList<Any?>()
    if (!historical || (snapshot && maximum == Version.HEAD.number)) {
      terms.add("next_version = ?")
      values.add(Long.MAX_VALUE)
    }
    if (maximum != Version.HEAD.number) {
      terms.add("version <= ?")
      values.add(maximum ushr 2)
      if (snapshot) {
        terms.add("next_version > ?")
        values.add(maximum ushr 2)
      }
    }
    if (minimum != null) {
      terms.add("version >= ?")
      values.add(if (minimum == Version.HEAD.number) Long.MAX_VALUE else minimum ushr 2)
    }
    if (versions == 1 && !queryDeleted) terms.add("operation != 'D'")
    predicate = if (terms.isEmpty()) "TRUE" else terms.joinToString(" AND ")
    parameters = Collections.unmodifiableList(values)
  }

  /**
   * Returns a SELECT of id, i, version and operation, without LIMIT or OFFSET. The caller supplies a safely quoted table, trusted SQL ordering, and a nonempty full combined predicate already containing this helper's predicate exactly once; this method does not append it. Parenthesize caller OR expressions when combining predicates.
   *
   * Bind parameters in their textual predicate order (for example, parameters followed by query parameters when this predicate comes first), then bind any caller-appended pagination. Caller filters run before ranking; deletion suppression and per-ID rank limits run afterward. The outer ordering is applied after selection and does not change the ranking.
   * @since 3.0
   */
  fun select(table: String, predicate: String, orderBy: String): String {
    if (versions == 1) return "SELECT id, i, version, operation FROM $table WHERE $predicate ORDER BY $orderBy"
    val ordering = "version DESC, CASE operation WHEN 'I' THEN 0 WHEN 'U' THEN 1 WHEN 'D' THEN 2 ELSE 3 END DESC, i DESC"
    val deleted = if (queryDeleted) "" else " AND latest_operation != 'D' AND operation != 'D'"
    return "WITH candidates AS (SELECT *, row_number() OVER (PARTITION BY id ORDER BY $ordering) AS history_rank, " +
      "first_value(operation) OVER (PARTITION BY id ORDER BY $ordering) AS latest_operation " +
      "FROM $table WHERE $predicate) SELECT id, i, version, operation FROM candidates " +
      "WHERE history_rank <= $versions$deleted ORDER BY $orderBy"
  }
}
