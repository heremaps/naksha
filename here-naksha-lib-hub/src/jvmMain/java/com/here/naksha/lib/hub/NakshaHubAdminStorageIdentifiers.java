package com.here.naksha.lib.hub;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NakshaHubAdminStorageIdentifiers {

  private static final Logger logger = LoggerFactory.getLogger(NakshaHubAdminStorageIdentifiers.class);

  private NakshaHubAdminStorageIdentifiers() {
  }

  private static final String HUB_ADMIN_STORAGE_ENV_NAME = "HUB_ADMIN_STORAGE_ID";
  private static final String HUB_ADMIN_MAP_ENV_NAME = "HUB_ADMIN_MAP_ID";
  private static final String DEFAULT_HUB_ADMIN_STORAGE_ID = "naksha-hub-admin-storage";
  private static final String DEFAULT_HUB_ADMIN_MAP_ID = "naksha-hub-admin";

  /**
   * Returns the Hub Admin Storage ID, based on the environment variable {@code HUB_ADMIN_STORAGE_ID}. If the environment variable is not
   * set, the default value {@code naksha-hub-admin-storage} is returned.
   */
  public static String getHubAdminStorageId() {
    final String envValue = System.getenv(HUB_ADMIN_STORAGE_ENV_NAME);
    if (envValue == null) {
      logger.info(
          "Environment variable {} is not set. Using default value for Hub Admin StorageID: {}",
          HUB_ADMIN_STORAGE_ENV_NAME, DEFAULT_HUB_ADMIN_STORAGE_ID
      );
      return DEFAULT_HUB_ADMIN_STORAGE_ID;
    } else {
      logger.info(
          "Environment variable {} is set. Using value for Hub Admin StorageID: {}",
          HUB_ADMIN_STORAGE_ENV_NAME, envValue);
      return envValue;
    }
  }

  /**
   * Returns the Hub Admin Map ID, based on the environment variable {@code HUB_ADMIN_MAP_ID}. If the environment variable is not set, the
   * default value {@code naksha-hub-admin} is returned.
   */
  public static String getHubAdminMapId() {
    final String envValue = System.getenv(HUB_ADMIN_MAP_ENV_NAME);
    if (envValue == null) {
      logger.info(
          "Environment variable {} is not set. Using default value for Hub Admin MapID: {}",
          HUB_ADMIN_MAP_ENV_NAME, DEFAULT_HUB_ADMIN_MAP_ID
      );
      return DEFAULT_HUB_ADMIN_MAP_ID;
    } else {
      logger.info(
          "Environment variable {} is set. Using value for Hub Admin MapID: {}",
          HUB_ADMIN_MAP_ENV_NAME, envValue);
      return envValue;
    }
  }
}
