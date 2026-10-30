package naksha.iml

import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID
import naksha.model.Naksha
import naksha.model.NakshaContext
import org.junit.jupiter.api.Assumptions.assumeTrue

/** Local, opt-in fixture owning only two randomly named schemas. Never installs extensions. @since 3.0 */
internal class ImlTestFixture private constructor(val connection: Connection, url: String, quotedNames: Boolean) : AutoCloseable {
  private val suffix = UUID.randomUUID().toString().replace("-", "")
  val schema = "iml_test_$suffix" + if (quotedNames) "\";--" else ""
  val metadataSchema = "iml_meta_$suffix" + if (quotedNames) "\";--" else ""
  val layer = "roads"
  val otherLayer = "places' OR TRUE --"
  val table = "roads\"; SELECT 1;--"
  val otherTable = "places"
  val sqlTable = "${quote(schema)}.${quote(table)}"
  val sqlMetadata = "${quote(metadataSchema)}.space_meta"
  val config = ImlConfig("iml-test-$suffix", url).apply {
    schema = this@ImlTestFixture.schema
    metadataSchema = this@ImlTestFixture.metadataSchema
    hardCap = 100
  }
  val storage: ImlStorage get() = Naksha.useStorage(config) as ImlStorage
  private val ownedSchemas = mutableListOf<String>()

  private fun initialize() {
    // Fail explicitly if the opted-in database lacks PostGIS; setup belongs to the caller.
    connection.createStatement().use { statement ->
      statement.executeQuery("SELECT PostGIS_Version()").use { check(it.next()) }
      for (name in arrayOf(schema, metadataSchema)) {
        statement.execute("CREATE SCHEMA ${quote(name)}")
        ownedSchemas.add(name)
      }
      statement.execute("CREATE TABLE $sqlMetadata (id text, schem text, h_id text, meta jsonb)")
      for (name in arrayOf(table, otherTable)) {
        statement.execute("CREATE TABLE ${quote(schema)}.${quote(name)} (" +
          "id text, version bigint, next_version bigint DEFAULT 9223372036854775807, " +
          "operation char(1), author text, jsondata jsonb, geo geometry(GeometryZ,4326), i bigint)")
      }
    }
    addLayer(layer, table)
    addLayer(otherLayer, otherTable)
    addLayer("ignored-other-schema", "missing", "not_$schema")
    insert("00123", 7, "U", "alice", "POINT Z (1 2 3)", 11)
    insert(INJECTION_ID, 8, "I", "al_%ice", "LINESTRING Z (0 0 4, 3 3 5)", 12)
    insert("far", 9, "I", "bob", "POINT Z (40 50 6)", 13)
    insert("null-geo", 10, "I", null, null, 14)
    insert("deleted", 11, "D", "alice", "POINT Z (1 2 9)", 15)
    insert("unknown", 12, "X", "albert", "POINT Z (1 2 8)", 16)
    insert("00123", 1, "I", "old-author", "POINT Z (60 60 7)", 1, nextVersion = 7)
    insert("second-layer", 1, "I", "alice", null, 11, target = otherTable)
  }

  fun addLayer(id: String, table: String, layerSchema: String = schema) {
    connection.prepareStatement("INSERT INTO $sqlMetadata VALUES (?, ?, ?, '{}'::jsonb)").use {
      it.setString(1, id)
      it.setString(2, layerSchema)
      it.setString(3, table)
      it.executeUpdate()
    }
  }

  fun insert(id: String, version: Long, operation: String, author: String?, geometry: String?, number: Long,
    nextVersion: Long = Long.MAX_VALUE, target: String = table) {
    connection.prepareStatement("INSERT INTO ${quote(schema)}.${quote(target)} " +
      "(id, version, next_version, operation, author, jsondata, geo, i) " +
      "VALUES (?, ?, ?, ?, ?, ?::jsonb, ST_GeomFromText(?, 4326), ?)").use {
      it.setString(1, id)
      it.setLong(2, version)
      it.setLong(3, nextVersion)
      it.setString(4, operation)
      it.setString(5, author)
      it.setString(6, """{"type":"Feature","id":"stale-json-id","geometry":{"type":"Point","coordinates":[99,80,1]},"properties":{"custom":"kept","@ns:com:here:xyz":{"author":"stale-json-author","createdAt":1000,"updatedAt":2000,"authorTimestamp":1500,"tags":["blue","test"],"unrecognized":"keep-me","nestedUnknown":{"answer":42}}}}""")
      it.setString(7, geometry)
      it.setLong(8, number)
      it.executeUpdate()
    }
  }

  override fun close() {
    try {
      Naksha.removeStorage(config)
    } finally {
      try {
        for (name in ownedSchemas.asReversed()) {
          connection.createStatement().use { it.execute("DROP SCHEMA ${quote(name)} CASCADE") }
        }
      } finally {
        connection.close()
      }
    }
  }

  companion object {
    const val INJECTION_ID = "x' OR TRUE; DROP TABLE space_meta; --"

    fun withFixture(quotedNames: Boolean = false, block: (ImlTestFixture) -> Unit) {
      val url = System.getenv("NAKSHA_TEST_IML_DB_URL")
      assumeTrue(!url.isNullOrBlank(), "Set NAKSHA_TEST_IML_DB_URL to a local fixture-capable PostgreSQL database with PostGIS")
      NakshaContext.currentContext().withAppId("iml-test")
      val fixture = ImlTestFixture(DriverManager.getConnection(url), url.orEmpty(), quotedNames)
      fixture.use {
        it.initialize()
        block(it)
      }
    }

    private fun quote(identifier: String) = "\"${identifier.replace("\"", "\"\"")}\""
  }
}
