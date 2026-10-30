package naksha.iml

import naksha.base.NotNullProperty
import naksha.model.objects.NakshaStorage

/**
 * Configuration for the read-only Interactive Map Layers storage.
 * @since 3.0
 */
open class ImlConfig() : NakshaStorage() {

  /**
   * Creates a storage configuration using a PostgreSQL JDBC URL. Credentials may be supplied in the URL; do not log or persist it unprotected.
   * @since 3.0
   */
  constructor(id: String, jdbcUrl: String) : this() {
    this.id = id
    this.jdbcUrl = jdbcUrl
    this.className = defaultClassName()
  }

  override fun defaultClassName(): String = "naksha.iml.ImlStorage"

  companion object ImlConfig_C {
    private val JDBC_URL = NotNullProperty<ImlConfig, String>(String::class) { _, _ -> "" }
    private val SCHEMA = NotNullProperty<ImlConfig, String>(String::class) { _, _ -> "public" }
    private val METADATA_SCHEMA = NotNullProperty<ImlConfig, String>(String::class) { _, _ -> "xyz_config" }
  }

  /**
   * The PostgreSQL JDBC connection URL, including any driver-specific connection options.
   * @since 3.0
   */
  var jdbcUrl by JDBC_URL

  /**
   * The layer schema exposed as a catalog; defaults to public. Override for isolated local fixtures.
   * @since 3.0
   */
  var schema by SCHEMA

  /**
   * The schema containing space_meta; defaults to xyz_config.
   * @since 3.0
   */
  var metadataSchema by METADATA_SCHEMA

  /**
   * Sets the JDBC URL and returns this configuration.
   * @since 3.0
   */
  fun withJdbcUrl(value: String): ImlConfig {
    jdbcUrl = value
    return this
  }

  override fun withId(value: String): ImlConfig = super.withId(value) as ImlConfig

  override fun configEquals(other: NakshaStorage): Boolean {
    if (className != other.className || id != other.id) return false
    val config = other.proxy(ImlConfig::class)
    return jdbcUrl == config.jdbcUrl && schema == config.schema && metadataSchema == config.metadataSchema && hardCap == config.hardCap &&
      create == config.create && upgrade == config.upgrade
  }
}
