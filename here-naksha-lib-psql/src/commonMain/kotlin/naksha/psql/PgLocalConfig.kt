package naksha.psql

import naksha.base.AnyObject
import naksha.base.Id
import naksha.base.Id.IdCompanion.ADMIN_CATALOG_QUOTED
import naksha.base.IdTextType
import naksha.base.Platform
import naksha.base.PlatformMap
import naksha.base.illegalState
import naksha.model.NakshaVersion
import naksha.psql.PgUtil.PgUtilCompanion.quoteLiteral

/**
 * Credential-free bootstrap information stored in the admin schema comment. Version 1 allows only the fields listed below; storage numbers and packed admin versions are decimal strings, never JSON numbers. Connection details come exclusively from the active SQL session.
 * @since 3.0
 */
internal object PgLocalConfig {
  private const val FORMAT = "naksha.psql.local-config"
  private val FIELDS = setOf(
    "format", "version", "storageId", "storageNumber", "adminVersion", "hardCap",
    "temp_tablespace", "brittle_tablespace", "ephemeral_tablespace"
  )

  /**
   * Encode a fresh allowlisted object, never the storage configuration or its metadata.
   * @since 3.0
   */
  fun encode(config: PgConfig, storageId: Id, version: NakshaVersion): String {
    require(config.id == storageId.text) { "Local config storage ID differs from the configuration" }
    require(storageId.textType !== IdTextType.ILLEGAL) { "Illegal local config storage ID" }
    require(Id(storageId.text).number == storageId.number) { "Local config storage number differs from its ID" }
    return Platform.toJSON(Platform.newMap(
      "format", FORMAT,
      "version", 1,
      "storageId", storageId.text,
      "storageNumber", storageId.number.toString(),
      "adminVersion", version.toLong().toString(),
      "hardCap", config.hardCap,
      "temp_tablespace", config.temp_tablespace,
      "brittle_tablespace", config.brittle_tablespace,
      "ephemeral_tablespace", config.ephemeral_tablespace
    ))
  }

  /**
   * Replace the admin schema comment in the caller's transaction, after installation of the identity functions.
   * @since 3.0
   */
  fun writeToSchemaComment(conn: PgConnection, config: PgConfig, storageId: Id, version: NakshaVersion) {
    val json = encode(config, storageId, version)
    conn.execute("COMMENT ON SCHEMA $ADMIN_CATALOG_QUOTED IS ${quoteLiteral(json)}").close()
  }

  /**
   * Read and verify the comment against installed SQL identity functions. Cast bigint results to text before they reach PLV8, avoiding JavaScript number precision loss. This does not install, upgrade, or commit anything.
   * @since 3.0
   */
  fun readFromSchemaComment(conn: PgConnection): PgConfig {
    conn.execute(
      """SELECT pg_catalog.obj_description(n.oid, 'pg_namespace') AS config,
        |current_database()::text AS db, current_user::text AS role,
        |inet_server_addr()::text AS host, inet_server_port() AS port,
        |$ADMIN_CATALOG_QUOTED.naksha_storage_id() AS storage_id,
        |$ADMIN_CATALOG_QUOTED.naksha_storage_number()::text AS storage_number,
        |$ADMIN_CATALOG_QUOTED.naksha_version()::text AS admin_version
        |FROM pg_catalog.pg_namespace n WHERE n.nspname = 'naksha~admin'
      """.trimMargin()
    ).use { cursor ->
      if (!cursor.next()) throw illegalState("Missing Naksha admin schema")
      val json = cursor.column("config") as? String
        ?: throw illegalState("Missing Naksha local config schema comment")
      try {
        return decode(
          json, cursor["db"], cursor["role"], cursor["storage_id"],
          cursor["storage_number"], cursor["admin_version"],
          cursor.column("host") as? String, cursor.column("port") as? Int
        )
      } catch (e: Exception) {
        throw illegalState("Invalid Naksha local config schema comment: ${e.message}", e)
      }
    }
  }

  /**
   * Decode and validate version 1 against ambient database identity. The installed version need not equal this library's version; runtime compatibility checks remain the database bootstrap's responsibility. Missing server address/port (Unix sockets) use localhost/5432.
   * @since 3.0
   */
  fun decode(
    json: String,
    currentDatabase: String,
    currentUser: String,
    installedStorageId: String,
    installedStorageNumber: String,
    installedAdminVersion: String,
    host: String? = null,
    port: Int? = null
  ): PgConfig {
    val parsed = try {
      Platform.fromJSON(json)
    } catch (e: Exception) {
      throw IllegalArgumentException("Invalid local config JSON", e)
    }
    require(parsed is PlatformMap) { "Local config must be a JSON object" }
    val data = parsed.proxy(AnyObject::class)
    for (field in data.keys) require(field in FIELDS) { "Unknown local config field" }
    require(data.getRaw("format") == FORMAT) { "Unknown local config format" }
    val formatVersion = data.getRaw("version")
    require(formatVersion is Number && formatVersion.toDouble() == 1.0) { "Unsupported local config version" }
    val idText = data.getRaw("storageId")
    require(idText is String && IdTextType.classify(idText) !== IdTextType.ILLEGAL) { "Illegal local config storage ID" }
    val id = Id(idText)
    val number = decimalLong(data.getRaw("storageNumber"))
    require(id.number == number) { "Local config storage number differs from its ID" }
    require(idText == installedStorageId && number == decimalLong(installedStorageNumber)) {
      "Local config differs from installed storage identity"
    }
    val packedVersion = decimalLong(data.getRaw("adminVersion"))
    require(NakshaVersion(packedVersion).toLong() == packedVersion) { "Invalid packed admin version" }
    require(packedVersion == decimalLong(installedAdminVersion)) { "Local config differs from installed admin version" }
    val hardCap = data.getRaw("hardCap")
    require(hardCap is Number && hardCap.toDouble() == hardCap.toInt().toDouble()) { "Invalid local config hardCap" }
    require(currentDatabase.isNotEmpty() && currentUser.isNotEmpty()) { "Missing ambient database or role" }
    require(port == null || port in 1..65535) { "Invalid ambient server port" }
    val config = PgConfig(idText)
    config.className = "naksha.psql.PgStorage"
    config.create = false
    config.upgrade = false
    config.setRaw("override", false)
    config.hardCap = hardCap.toInt()
    for (field in arrayOf("temp_tablespace", "brittle_tablespace", "ephemeral_tablespace")) {
      val value = data.getRaw(field)
      require(value == null || value is String) { "Invalid local config tablespace" }
      if (value != null) config.setRaw(field, value)
    }
    config.master = PgInstanceConfig()
      .withDb(currentDatabase).withUser(currentUser).withPassword("")
      .withHost(host ?: "localhost").withPort(port ?: 5432)
    return config
  }

  /**
   * Require canonical decimal text so no 64-bit value passes through a JSON/JavaScript number.
   * @since 3.0
   */
  private fun decimalLong(value: Any?): Long {
    require(value is String) { "Local config integer must be decimal text" }
    val number = value.toLongOrNull()
    require(number != null && number.toString() == value) { "Invalid local config decimal integer" }
    return number
  }
}
