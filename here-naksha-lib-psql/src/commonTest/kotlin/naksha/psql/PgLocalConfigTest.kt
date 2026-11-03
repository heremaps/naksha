package naksha.psql

import naksha.base.AnyObject
import naksha.base.Id
import naksha.base.Platform
import naksha.base.PlatformMap
import naksha.model.NakshaVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PgLocalConfigTest {
  private val version = NakshaVersion.of("3.0.0-beta.52")

  @Test
  fun roundTripsUnicodeIdentityAndEscapedTablespacesWithoutCredentials() {
    val id = Id("storage \u00e4\u6771\ud83c\udf0d\\path")
    val config = PgConfig(id.text)
    config.master = PgInstanceConfig().withHost("private-host").withDb("private-db")
      .withUser("private-user").withPassword("master-secret")
    config.replicas.addUri("jdbc:postgresql://private-replica/private-db?user=private-user&password=replica-secret")
    config.setRaw("author", "author-secret")
    config.setRaw("metadata", Platform.newMap("token", "metadata-secret"))
    config.setRaw("arbitrary", "raw-secret")
    config.setRaw("override", true)
    config.create = true
    config.upgrade = true
    config.hardCap = 12345
    val tablespace = "tablespace '\"\\\n\t\u00e4\u6771"
    config.setRaw("temp_tablespace", tablespace)
    config.setRaw("brittle_tablespace", "brittle")
    config.setRaw("ephemeral_tablespace", "ephemeral")

    val json = PgLocalConfig.encode(config, id, version)
    val data = (Platform.fromJSON(json) as PlatformMap).proxy(AnyObject::class)
    assertEquals(9, data.size)
    assertEquals(id.text, data.getRaw("storageId"))
    assertEquals(id.number.toString(), data.getRaw("storageNumber"))
    assertEquals(version.toLong().toString(), data.getRaw("adminVersion"))
    for (secret in arrayOf("private-", "secret", "master", "replica", "author", "metadata", "arbitrary")) {
      assertFalse(json.contains(secret), "Unexpected field or credential: $secret")
    }

    val restored = decode(json, id)
    assertEquals(id.text, restored.id)
    assertEquals(id.number, restored.databaseNumber)
    assertEquals("naksha.psql.PgStorage", restored.className)
    assertEquals(12345, restored.hardCap)
    assertEquals(tablespace, restored.temp_tablespace)
    assertEquals("brittle", restored.brittle_tablespace)
    assertEquals("ephemeral", restored.ephemeral_tablespace)
    assertFalse(restored.create)
    assertFalse(restored.upgrade)
    assertFalse(restored.override == true)
    assertNull(restored.version)
    assertTrue(restored.replicas.isEmpty())
    assertEquals("ambient-db", restored.master.db)
    assertEquals("ambient-role", restored.master.user)
    assertEquals("", restored.master.password)
    assertEquals("localhost", restored.master.host)
    assertEquals(5432, restored.master.port)
  }

  @Test
  fun preservesNumericIdsBeyondJavaScriptPrecisionAndAmbientServer() {
    for (text in arrayOf("9007199254740993", Long.MAX_VALUE.toString(), "0", "named-storage")) {
      val id = Id(text)
      val json = PgLocalConfig.encode(PgConfig(text), id, version)
      val restored = PgLocalConfig.decode(
        json, "db", "role", text, id.number.toString(), version.toLong().toString(), "192.0.2.1", 15432
      )
      assertEquals(text, restored.id)
      assertEquals(id.number, restored.databaseNumber)
      assertEquals("192.0.2.1", restored.master.host)
      assertEquals(15432, restored.master.port)
      assertNull(restored.temp_tablespace)
    }
  }

  @Test
  fun acceptsInstalledVersionIndependentlyOfLibraryAndPreservesHardCapSentinels() {
    val id = Id("local-storage")
    val installedVersion = NakshaVersion.of("65535.0.0")
    for (hardCap in arrayOf(Int.MIN_VALUE, -1, 0, Int.MAX_VALUE)) {
      val config = PgConfig(id.text)
      config.hardCap = hardCap
      val restored = PgLocalConfig.decode(
        PgLocalConfig.encode(config, id, installedVersion), "db", "role", id.text,
        id.number.toString(), installedVersion.toLong().toString()
      )
      assertEquals(hardCap, restored.hardCap)
      assertNull(restored.version)
    }
  }

  @Test
  fun rejectsMalformedDocumentsAndNonAllowlistedFields() {
    val id = Id("test-storage")
    val json = PgLocalConfig.encode(PgConfig(id.text), id, version)
    for (invalid in arrayOf("", "{", "null", "[]", "true", "\"text\"", "{}")) {
      assertFailsWith<IllegalArgumentException> { decode(invalid, id) }
    }
    val changes = arrayOf(
      "format" to "unknown", "version" to 2, "version" to "1", "version" to null, "version" to true,
      "storageId" to null, "storageId" to 123, "storageId" to "", "storageId" to "bad'id",
      "storageId" to "bad\nname", "storageId" to "\ud800", "storageId" to "other-storage",
      "storageNumber" to id.number, "storageNumber" to "9223372036854775808",
      "storageNumber" to "01", "storageNumber" to "+1", "storageNumber" to "1",
      "adminVersion" to version.toLong(), "adminVersion" to "0", "adminVersion" to null,
      "hardCap" to null, "hardCap" to "123", "hardCap" to 1.5, "hardCap" to 2147483648.0,
      "temp_tablespace" to 1, "brittle_tablespace" to false, "ephemeral_tablespace" to Platform.newMap(),
      "master" to "credential", "replicas" to "credential", "author" to "metadata",
      "create" to true, "upgrade" to true, "override" to true, "__proto__" to Platform.newMap()
    )
    for ((field, value) in changes) {
      val data = (Platform.fromJSON(json) as PlatformMap).proxy(AnyObject::class)
      data.setRaw(field, value)
      assertFailsWith<IllegalArgumentException>("Accepted invalid field $field") {
        decode(Platform.toJSON(data), id)
      }
    }
    for (field in arrayOf("format", "version", "storageId", "storageNumber", "adminVersion", "hardCap")) {
      val data = (Platform.fromJSON(json) as PlatformMap).proxy(AnyObject::class)
      data.remove(field)
      assertFailsWith<IllegalArgumentException>("Accepted missing field $field") {
        decode(Platform.toJSON(data), id)
      }
    }
  }

  @Test
  fun rejectsInstalledIdentityAndVersionMismatches() {
    val id = Id("test-storage")
    val json = PgLocalConfig.encode(PgConfig(id.text), id, version)
    assertFailsWith<IllegalArgumentException> {
      PgLocalConfig.decode(json, "db", "role", "other", id.number.toString(), version.toLong().toString())
    }
    assertFailsWith<IllegalArgumentException> {
      PgLocalConfig.decode(json, "db", "role", id.text, "1", version.toLong().toString())
    }
    assertFailsWith<IllegalArgumentException> {
      PgLocalConfig.decode(json, "db", "role", id.text, id.number.toString(), NakshaVersion.of("3.0.0").toLong().toString())
    }
    assertFailsWith<IllegalArgumentException> {
      PgLocalConfig.encode(PgConfig("other"), id, version)
    }
    assertFailsWith<IllegalArgumentException> {
      PgLocalConfig.encode(PgConfig(id.text), Id(id.text, 1L, true), version)
    }
  }

  @Test
  fun rejectsMissingAmbientIdentityAndInvalidPort() {
    val id = Id("local-storage")
    val json = PgLocalConfig.encode(PgConfig(id.text), id, version)
    for ((db, role, port) in arrayOf(Triple("", "role", 5432), Triple("db", "", 5432),
      Triple("db", "role", 0), Triple("db", "role", 65536))) {
      assertFailsWith<IllegalArgumentException> {
        PgLocalConfig.decode(json, db, role, id.text, id.number.toString(), version.toLong().toString(), port = port)
      }
    }
  }

  /**
   * Decode with a matching installed identity and independent ambient connection information.
   * @since 3.0
   */
  private fun decode(json: String, id: Id): PgConfig = PgLocalConfig.decode(
    json, "ambient-db", "ambient-role", id.text, id.number.toString(), version.toLong().toString()
  )
}
