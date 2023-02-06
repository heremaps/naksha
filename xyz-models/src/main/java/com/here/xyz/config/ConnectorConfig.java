package com.here.xyz.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.here.xyz.util.JsonConfigFile;
import com.here.xyz.util.JsonFilename;
import org.jetbrains.annotations.NotNull;

/**
 * The configuration for the connector. This config is used for the embedded version and the stand-alone version (when being deployed as
 * verticle).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonFilename("connector-config.json")
public class ConnectorConfig extends JsonConfigFile<ConnectorConfig> {

  public ConnectorConfig() {
    super(null);
  }

  public ConnectorConfig(@NotNull String filename) {
    super(filename);
  }

  /**
   * Returns the current configuration.
   *
   * @return the config.
   * @throws IllegalStateException if loading the configuration failed.
   */
  public static @NotNull ConnectorConfig get() throws IllegalStateException {
    return JsonConfigFile.get(ConnectorConfig.class);
  }

  @JsonProperty
  public int HTTP_PORT = 9090;

  @JsonProperty
  public String ECPS_PASSPHRASE = "local";

  @JsonProperty
  public int DB_INITIAL_POOL_SIZE = 5;

  @JsonProperty
  public int DB_MIN_POOL_SIZE = 1;

  @JsonProperty
  public int DB_MAX_POOL_SIZE = 50;

  @JsonProperty
  public int DB_ACQUIRE_RETRY_ATTEMPTS = 10;

  @JsonProperty
  public int DB_ACQUIRE_INCREMENT = 1;

  @JsonProperty
  public int DB_CHECKOUT_TIMEOUT = 10;

  @JsonProperty
  public boolean DB_TEST_CONNECTION_ON_CHECKOUT;

  @JsonProperty
  public int DB_STATEMENT_TIMEOUT_IN_S = 10;

  @JsonProperty
  public int MAX_CONCURRENT_MAINTENANCE_TASKS = 1;

  @JsonProperty
  public int MISSING_MAINTENANCE_WARNING_IN_HR = 12;
}