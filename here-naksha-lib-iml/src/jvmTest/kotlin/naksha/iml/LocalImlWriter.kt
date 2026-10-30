package naksha.iml

import naksha.model.Naksha

/** Optional local fixture helper, not an IWriteSession or production writer. Only mutates the fixture-owned random-schema table. @since 3.0 */
internal class LocalImlWriter(private val fixture: ImlTestFixture) {
  /** Put a revision using an unpacked, strictly increasing source version and optional relational WKT geometry. @since 3.0 */
  fun put(id: String, sourceVersion: Long, author: String, json: String, geometryWkt: String? = null) {
    write(id, sourceVersion, author, json, geometryWkt)
  }

  /** Append a bodyless tombstone, retaining prior revisions for history reads. @since 3.0 */
  fun delete(id: String, sourceVersion: Long, author: String) {
    write(id, sourceVersion, author, null, null)
  }

  private fun write(id: String, sourceVersion: Long, author: String, json: String?, geometryWkt: String?) {
    ImlTestFixture.validateLocalUrl(fixture.connection.metaData.url)
    ImlTuple.version(sourceVersion, "I")
    require(sourceVersion != Long.MAX_VALUE) { "HEAD is not a source revision" }
    val connection = fixture.connection
    val table = fixture.sqlTable
    synchronized(connection) {
      check(connection.autoCommit) { "LocalImlWriter must own its transaction" }
      connection.autoCommit = false
      try {
        // A table-scoped transaction lock covers absent IDs as well as max(i) allocation across all IDs.
        connection.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))").use {
          it.setString(1, table)
          it.executeQuery().use { result -> check(result.next()) }
        }
        var operation = "I"
        connection.prepareStatement("SELECT version, next_version, operation FROM $table WHERE id = ? ORDER BY version DESC FOR UPDATE").use {
          it.setString(1, id)
          it.executeQuery().use { result ->
            if (result.next()) {
              require(sourceVersion > result.getLong("version")) { "Source versions must strictly increase per ID" }
              check(result.getLong("next_version") == Long.MAX_VALUE) { "Existing latest revision must be HEAD" }
              if (result.getString("operation") != "D") operation = "U"
            }
          }
        }
        connection.prepareStatement("UPDATE $table SET next_version = ? WHERE id = ? AND next_version = ?").use {
          it.setLong(1, sourceVersion)
          it.setString(2, id)
          it.setLong(3, Long.MAX_VALUE)
          check(it.executeUpdate() <= 1) { "Multiple HEAD revisions for one ID" }
        }
        connection.prepareStatement("INSERT INTO $table (id, version, next_version, operation, author, jsondata, geo, i) " +
          "SELECT ?, ?, ?, ?, ?, ?::jsonb, ST_GeomFromText(?, 4326), COALESCE(max(i), 0) + 1 FROM $table").use {
          it.setString(1, id)
          it.setLong(2, sourceVersion)
          it.setLong(3, Long.MAX_VALUE)
          it.setString(4, if (json == null) "D" else operation)
          it.setString(5, author)
          it.setString(6, json)
          it.setString(7, geometryWkt)
          check(it.executeUpdate() == 1)
        }
        connection.commit()
      } catch (error: Throwable) {
        try {
          connection.rollback()
        } catch (rollbackError: Throwable) {
          error.addSuppressed(rollbackError)
        }
        throw error
      } finally {
        connection.autoCommit = true
      }
      val storage = Naksha.getStorageById(fixture.config.id)
      if (storage != null) Naksha.cache.clear(storage)
    }
  }
}
