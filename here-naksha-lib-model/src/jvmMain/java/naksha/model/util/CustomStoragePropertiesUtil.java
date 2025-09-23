package naksha.model.util;

import java.util.Optional;
import naksha.base.JvmAnyObjectUtil;
import naksha.model.IStorage;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaStorage;
import org.jetbrains.annotations.Nullable;

/**
 * This util is for fetching custom Storage properties - ones that are not part of {@link NakshaStorage} but are essential from Hub
 * perspective. Properties fetched by this class are part of NakshaHub API contract that was introduced in V2 or earlier (see
 * 'openapi/yaml') but because of design choice they are not appropriate in V3 model of {@link NakshaStorage}
 * <p>
 * This util serves hacky and duck-typing based model which should not be part of Hub and related modules (ie 'app-service'). It shows the
 * need for additional DTO layer between Hub and low level module. Keeping Hub-only model for Storage (and other admin resources) will allow
 * decoupling and removal of such utils as this one.
 * <p>
 * This should be fixed as part of CASL-1304
 */
// TODO: part of CASL-1304 refactor
@Deprecated
public class CustomStoragePropertiesUtil {

  private CustomStoragePropertiesUtil() {
  }


  /**
   * Prepares session options based on custom storage configuration. It combines original options with properties defined on the storage level.
   * If storage config is missing, the original session options are used.
   * If given storage property is missing (ie some timeout) or the custom config is not available, the value from original options will be used
   *
   * @param baseOptions original session options
   * @param storage storage for which the session options will apply upon session opening, potentially holding custom configuration
   * @return session options containing resolved properties
   */
  public static SessionOptions mergeSessionOptionsWithStorageConfig(SessionOptions baseOptions, IStorage storage) {
    try {
      NakshaStorage storageConfig = storage.getConfig(); // this might throw exception for some implementations
      if (storageConfig == null) {
        return baseOptions;
      }
      return baseOptions.copyWithTimeouts(
          CustomStoragePropertiesUtil.getSocketTimeoutMs(storageConfig),
          CustomStoragePropertiesUtil.getConnectTimeoutMs(storageConfig),
          CustomStoragePropertiesUtil.getStmtTimeoutMs(storageConfig),
          CustomStoragePropertiesUtil.getLockTimeoutMs(storageConfig)
      );
    } catch (Exception e) {
      return baseOptions;
    }
  }

  public static @Nullable Integer getConnectTimeoutMs(NakshaStorage storageConfig) {
    return getProperty(storageConfig, "connectTimeout", Integer.class)
        .map(CustomStoragePropertiesUtil::secondsToMilliseconds)
        .orElse(null);
  }

  public static @Nullable Integer getSocketTimeoutMs(NakshaStorage storageConfig) {
    return getProperty(storageConfig, "socketTimeout", Integer.class)
        .map(CustomStoragePropertiesUtil::secondsToMilliseconds)
        .orElse(null);
  }

  public static @Nullable Integer getStmtTimeoutMs(NakshaStorage storageConfig) {
    return getProperty(storageConfig, "stmtTimeout", Integer.class)
        .map(CustomStoragePropertiesUtil::secondsToMilliseconds)
        .orElse(null);
  }

  public static @Nullable Integer getLockTimeoutMs(NakshaStorage storageConfig) {
    return getProperty(storageConfig, "lockTimeout", Integer.class)
        .map(CustomStoragePropertiesUtil::secondsToMilliseconds)
        .orElse(null);
  }

  public static @Nullable String getSchema(NakshaStorage storageConfig) {
    return getProperty(storageConfig, "schema", String.class)
        .orElse(null);
  }

  private static Integer secondsToMilliseconds(Integer seconds) {
    return seconds * 1000;
  }

  private static <T> Optional<T> getProperty(NakshaStorage storageConfig, String propertyName, Class<T> klass) {
    return Optional.ofNullable(storageConfig.getProperties())
        .map(props -> JvmAnyObjectUtil.getProperty(props, propertyName, klass));
  }
}
