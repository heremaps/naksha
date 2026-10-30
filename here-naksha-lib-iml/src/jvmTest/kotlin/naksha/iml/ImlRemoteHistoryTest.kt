package naksha.iml

import java.util.UUID
import naksha.base.Action
import naksha.base.Version
import naksha.model.Naksha
import naksha.model.NakshaContext
import naksha.model.SessionOptions
import naksha.model.objects.StandardMembers
import naksha.model.objects.XyzMembers
import naksha.model.request.ReadFeatures
import naksha.model.request.SuccessResponse
import naksha.model.request.ops.And
import naksha.model.request.ops.Equals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

/** Opt-in smoke test using only read-only JDBC and API reads of an existing history layer. @since 3.0 */
class ImlRemoteHistoryTest {
  @Test
  fun `remote history snapshot and bounded versions agree with discovered source rows`() {
    val url = System.getenv("NAKSHA_TEST_IML_REMOTE_DB_URL")
    assumeTrue(!url.isNullOrBlank(), "Set NAKSHA_TEST_IML_REMOTE_DB_URL to enable read-only history smoke test")
    val layerId = System.getenv("NAKSHA_TEST_IML_REMOTE_HISTORY_LAYER").takeUnless { it.isNullOrBlank() }
      ?: "hrn:here-dev:data::olp-here:m499-source-01:data-02"
    NakshaContext.currentContext().withAppId("iml-remote-history-test")
    val config = ImlConfig("iml-remote-history-${UUID.randomUUID()}", url.orEmpty()).apply { hardCap = 100 }
    try {
      val storage = Naksha.useStorage(config) as ImlStorage
      storage.newReadSession(SessionOptions(connectTimeout = 15000, socketTimeout = 30000, stmtTimeout = 20000)).use { session ->
        val layer = storage.metadata.layersById[layerId]
        assertTrue(layer != null, "History layer must be present in discovered metadata")
        val table = checkNotNull(layer).sqlTable
        val source = session.withConnection { connection ->
          assertTrue(connection.isReadOnly, "Remote discovery connection must be read-only")
          connection.prepareStatement("SELECT id, version, next_version, i, operation FROM $table " +
            "WHERE next_version < ? ORDER BY version DESC LIMIT 1").use { statement ->
            statement.setLong(1, Long.MAX_VALUE)
            statement.executeQuery().use { rows ->
              assertTrue(rows.next(), "History layer must contain a historical row")
              SourceRow(rows.getString("id"), rows.getLong("version"), rows.getLong("next_version"),
                rows.getLong("i"), rows.getString("operation"))
            }
          }
        }
        val maximum = Version.manual(source.version, Action.VERSION).number
        fun read(versionsToRead: Int = 1, authorFilter: Boolean = false, author: String? = null): SuccessResponse =
          session.executeRead(ReadFeatures().apply {
            catalogId = config.schema
            collectionId = layerId
            queryHistory = true
            queryDeleted = true
            versions = versionsToRead
            version = maximum
            limit = 3
            queryMembers = if (authorFilter) {
              And(Equals(StandardMembers.Id, source.id), Equals(XyzMembers.XyzAuthor, author))
            } else Equals(StandardMembers.Id, source.id)
          }) as SuccessResponse

        val snapshot = read().featureTupleList
        assertTrue(snapshot.size == 1, "Snapshot must contain exactly one logical state")
        val selected = checkNotNull(snapshot[0])
        assertTrue(selected.tuple == null, "Snapshot selection must remain unloaded")
        session.loadTuples(snapshot)
        val tuple = checkNotNull(selected.tuple)
        assertTrue(tuple.id == source.id, "Snapshot ID must match discovered source")
        assertTrue(selected.tupleNumber.featureNumber == source.number, "Snapshot row number must match discovered source")
        assertTrue(selected.tupleNumber.version == source.packedVersion(), "Snapshot version and action must match discovered source")
        assertTrue(tuple.nextVersion == Version.manual(source.nextVersion, Action.VERSION).number,
          "Snapshot validity interval must match discovered source")
        assertTrue(selected.feature?.id == source.id, "Materialized snapshot must preserve source ID")
        assertTrue(selected.feature?.properties?.xyz?.getRaw("nuuid") == null, "No successor UUID may be fabricated")
        val author = tuple.getString(XyzMembers.XyzAuthor)

        for (filterAuthor in arrayOf(false, true)) {
          val expected = session.withConnection { connection ->
            assertTrue(connection.isReadOnly, "Remote history connection must be read-only")
            val authorPredicate = if (filterAuthor) " AND author IS NOT DISTINCT FROM ?" else ""
            connection.prepareStatement("SELECT id, version, next_version, i, operation FROM $table " +
              "WHERE id = ? AND version <= ?$authorPredicate ORDER BY version DESC, " +
              "CASE operation WHEN 'I' THEN 0 WHEN 'U' THEN 1 WHEN 'D' THEN 2 ELSE 3 END DESC, i DESC LIMIT 3").use { statement ->
              statement.setString(1, source.id)
              statement.setLong(2, source.version)
              if (filterAuthor) statement.setString(3, author)
              statement.executeQuery().use { rows ->
                val result = mutableListOf<SourceRow>()
                while (rows.next()) {
                  result.add(SourceRow(rows.getString("id"), rows.getLong("version"), rows.getLong("next_version"),
                    rows.getLong("i"), rows.getString("operation")))
                }
                result
              }
            }
          }
          val history = read(3, filterAuthor, author).featureTupleList
          assertTrue(history.size == expected.size && history.size in 1..3, "Bounded history count must match source")
          for (entry in history) assertTrue(entry?.tuple == null, "History selection must remain unloaded")
          session.loadTuples(history)
          val actual = mutableSetOf<Pair<Long, Long>>()
          val identities = mutableSetOf<Pair<Long, Long>>()
          for (row in expected) identities.add(row.number to row.packedVersion())
          for (entry in history) {
            val state = checkNotNull(entry)
            val loaded = checkNotNull(state.tuple)
            assertTrue(loaded.id == source.id && state.feature?.id == source.id, "History must retain logical source ID")
            assertTrue((state.tupleNumber.version ushr 2) <= source.version, "History must respect source sequence maximum")
            if (filterAuthor) assertTrue(loaded.getString(XyzMembers.XyzAuthor) == author, "History must respect source AUTHOR filter")
            actual.add(state.tupleNumber.featureNumber to state.tupleNumber.version)
          }
          assertTrue(actual == identities, "Bounded history identities must match source rows")
        }
      }
    } finally {
      Naksha.removeStorage(config)
    }
  }

  private data class SourceRow(val id: String, val version: Long, val nextVersion: Long, val number: Long, val operation: String) {
    fun packedVersion(): Long = Version.manual(version, when (operation) {
      "I" -> Action.CREATE
      "U" -> Action.UPDATE
      "D" -> Action.DELETE
      else -> Action.VERSION
    }).number
  }
}
