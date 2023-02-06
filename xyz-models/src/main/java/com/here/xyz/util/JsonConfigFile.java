package com.here.xyz.util;

import static java.io.File.separatorChar;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.here.xyz.util.ConfigCrypt.CryptoException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.commons.lang3.ObjectUtils.Null;
import org.jetbrains.annotations.NotNull;

/**
 * Base class of a configuration file that is read from a JSON file with an optional override via environment variables. The environment
 * variables are all required to be upper-cased and are optionally {@link #envPrefix() prefixed by a defined string}. The configuration file
 * should contain the member names in exact notation, unless annotated.
 *
 * @param <SELF> this type.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class JsonConfigFile<SELF extends JsonConfigFile<SELF>> extends FileOrResource<SELF> {

  protected JsonConfigFile() {
  }

  protected JsonConfigFile(@Nullable String filename) {
    super(filename);
  }

  protected JsonConfigFile(@Nullable String filename, @Nullable String searchPath) {
    super(filename, searchPath);
  }

  /**
   * The reference to the cached configurations.
   */
  private static final ConcurrentHashMap<Class<?>, JsonConfigFile<?>> cache = new ConcurrentHashMap<>();

  /**
   * Returns a cached configuration singleton.
   *
   * @param configClass the class of the configuration.
   * @return the config.
   * @throws NullPointerException if any given argument is null.
   * @throws RuntimeException     if any error occurred.
   */
  public static <C extends JsonConfigFile<C>> @Nonnull C get(@Nonnull Class<C> configClass) throws NullPointerException, RuntimeException {
    return get(configClass, null);
  }

  /**
   * Returns a cached configuration singleton.
   *
   * @param configClass the class of the configuration.
   * @param filename    the (optional) filename, either loaded from JAR or configured directory.
   * @return the config.
   * @throws NullPointerException if any given argument is null.
   * @throws RuntimeException     if any error occurred.
   */
  public static <C extends JsonConfigFile<C>> @Nonnull C get(@Nonnull Class<C> configClass, @Nullable String filename)
      throws RuntimeException {
    JsonConfigFile<?> raw = cache.get(configClass);
    if (configClass.isInstance(raw)) {
      return configClass.cast(raw);
    }
    if (raw != null) {
      throw new IllegalStateException("The cache contains an invalid value for " + configClass.getName());
    }
    synchronized (cache) {
      raw = cache.get(configClass);
      if (configClass.isInstance(raw)) {
        return configClass.cast(raw);
      }
      if (raw != null) {
        throw new IllegalStateException("The cache contains an invalid value for " + configClass.getName());
      }

      // We are alone in this synchronized block and need to load the config.
      try {
        if (filename == null) {
          filename = classNameToFileName(configClass);
        }
        final C config = configClass.getDeclaredConstructor().newInstance();
        config.withFilename(filename).load();
        cache.put(configClass, config);
        return config;
      } catch (Exception e) {
        if (e instanceof RuntimeException) {
          throw (RuntimeException) e;
        }
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * The value that represents an undefined key-id.
   */
  @SuppressWarnings("StringOperationCanBeSimplified")
  protected static final String UNDEFINED = new String("r2n8ZtxDiWGRYFVpCcitTyKFi9F9zhgECHiF9nDXJGjbLiuFrLYztp36WKMu");

  /**
   * The default key-id to be used with KMS.
   */
  public static final AtomicReference<String> defaultKeyId = new AtomicReference<>();

  /**
   * The key-id explicitly set or {@link #UNDEFINED}, if the default key-id should be used.
   */
  protected String keyId = UNDEFINED;

  /**
   * Returns the KMS key-id to be used to encrypt/decrypt secrets.
   *
   * @return the KMS key-id to be used to encrypt/decrypt secrets; if any.
   */
  public @Nullable String keyId() {
    //noinspection StringEquality
    return keyId == UNDEFINED ? defaultKeyId.get() : keyId;
  }

  /**
   * Explicitly set the key-id to the given value. If {@code null} is used, then encryption is disabled.
   *
   * @param keyId the key-id of the KMS key to use.
   * @return this.
   */
  public @Nonnull SELF withKeyId(@Nullable String keyId) {
    this.keyId = keyId;
    //noinspection unchecked
    return (SELF) this;
  }

  /**
   * Use the default key-id (restore default situation).
   *
   * @return this.
   */
  public @Nonnull SELF withDefaultKeyId() {
    keyId = UNDEFINED;
    //noinspection unchecked
    return (SELF) this;
  }

  /**
   * Returns the name of the environment variable that should be used to define the search path for the configuration files.
   * <p>
   * Optionally it is possible to add an {@link EnvName} annotation to the class, then this defines the name of the environment variable
   * that holds the search path.
   * <p>
   * In a nutshell, by default the method returns {@code XYZ_CONFIG_PATH}, if the {@link EnvName} annotation is found at the given class
   * type, for example {@code @EnvName("MY_CONFIG_PATH")}, then the method returns exactly this: {@code MY_CONFIG_PATH}.
   *
   * @param classType the class for which to check the annotations.
   * @return the name of the environment variable that holds the config file search path.
   */
  @Nonnull
  public static String configPathEnvName(@Nullable Class<?> classType) {
    while (classType != null) {
      if (classType.isAnnotationPresent(EnvName.class)) {
        final EnvName[] envNames = classType.getAnnotationsByType(EnvName.class);
        return envNames[0].value();
      }
      classType = classType.getSuperclass();
    }
    return "XYZ_CONFIG_PATH";
  }

  /**
   * An optional prefix to be placed in-front of all field names to be read from environment variables. So, the prefix "FOO_" will cause the
   * property "bar" to be looked-up as "FOO_BAR". If no prefix is returned, the environment variable name must explicitly be declared using
   * the annotation {@link EnvName}. Multiple names can be defined and combined with the prefix.
   *
   * @return a prefix or null.
   */
  @Nullable
  protected String envPrefix() {
    return null;
  }

  /**
   * A helper method to detect pseudo null values in environment variables. Without this, you can never undefine (unset) a pre-defined value
   * via environment variable. To allow this, this method treats an empty string and the string "null" as null (undefine).
   *
   * @param string the string that is read from the environment variable.
   * @return null if the value represents null; otherwise the given value.
   */
  @Nullable
  public static String nullable(@Nullable String string) {
    return string == null || string.isEmpty() || "null".equalsIgnoreCase(string) ? null : string;
  }

  /**
   * Used for Jackson to parse a JSON object into a map.
   */
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {
  };

  /**
   * Load the configuration from a disk file, a file in the resources (JAR) or from environment variables. Basically the order is to first
   * read from a disk file, if not found, read from resources. In both cases, eventually the environment variables will override all values
   * read from the configuration file or JAR.
   *
   * @return this.
   * @throws IOException if any error occurred while reading from disk or resources.
   */
  public final SELF load() throws IOException {
    return load(System::getenv);
  }

  /**
   * Load the configuration from a disk file, a file in the resources (JAR) or from environment variables. Basically the order is to first
   * read from a disk file, if not found, read from resources. In both cases, eventually the environment variables will override all values
   * read from the configuration file or JAR.
   *
   * @param getEnv the reference to the method that reads environment variables.
   * @return this.
   * @throws IOException          if any error occurred while reading from disk or resources.
   * @throws NullPointerException if getEnv is null.
   */
  public final SELF load(final @Nonnull Function<String, String> getEnv) throws IOException {
    Map<String, Object> configValues = null;
    try {
      String config_home = nullable(getEnv.apply(configPathEnvName(getClass())));
      if (config_home == null) {
        // If the user has to explicitly specified where the config-file is located, we follow the XDG Base Directory Specification:
        // https://specifications.freedesktop.org/basedir-spec/basedir-spec-latest.html
        // In a nutshell, by default we look into "$HOME/.config/xyz-hub/"!
        config_home = nullable(getEnv.apply("XDG_CONFIG_HOME"));
        if (config_home == null) {
          config_home = System.getProperty("user.home");
          if (config_home == null) {
            throw new IOException("Failed to find XDG_CONFIG_HOME");
          }
        }
        final char end = config_home.charAt(config_home.length() - 1);
        config_home += (end != '/' && end != '\\' ? separatorChar : "") + ".config/xyz-hub" + separatorChar;
      } else if (!config_home.endsWith("/") && !config_home.endsWith("\\")) {
        config_home += separatorChar;
      }

      try (final InputStream in = open(config_home)) {
        configValues = new ObjectMapper().readValue(in, MAP_TYPE);
      }
    } catch (Throwable t) {
      error("Failed to load configuration file: " + filename(), t);
    }
    return load(configValues, getEnv);
  }

  /**
   * Read the configuration from the given map and eventually use environment variables to override values.
   *
   * @param defaultValues the map to load values from.
   * @return this.
   */
  @Nonnull
  public final SELF load(@Nonnull Map<String, Object> defaultValues) {
    return load(defaultValues, System::getenv);
  }

  private Object decrypt(Object value) {
    if (value instanceof String) {
      final String string = (String) value;
      final String keyId = keyId();
      if (keyId != null) {
        try {
          return ConfigCrypt.decryptString(string, keyId);
        } catch (CryptoException e) {
          error("Failed to decrypt value", e);
        }
      }
    }
    return value;
  }

  /**
   * Read the configuration from a given map and eventually use environment variables to override all values read from the map.
   *
   * @param configValues the configuration values to use.
   * @param getEnv       the reference to the method that reads the environment variable; if null, then no environment variables read.
   * @return this.
   */
  @Nonnull
  public final SELF load(@Nullable Map<String, Object> configValues, @Nullable Function<String, String> getEnv) {
    final String prefix;
    final int prefixLen;
    final StringBuilder sb;
    if (getEnv != null) {
      sb = new StringBuilder(128);
      prefix = envPrefix();
      if (prefix != null) {
        prefixLen = prefix.length();
        for (int i = 0; i < prefix.length(); i++) {
          sb.append(Character.toUpperCase(prefix.charAt(i)));
        }
      } else {
        prefixLen = 0;
      }
    } else {
      prefix = null;
      prefixLen = 0;
      sb = null;
    }
    Class<?> theClass = getClass();
    do {
      for (final Field field : theClass.getDeclaredFields()) {
        String name = field.getName();
        if (field.isAnnotationPresent(JsonProperty.class)) {
          final String serializationName = field.getAnnotation(JsonProperty.class).value();
          if (serializationName != null && serializationName.length() > 0) {
            name = serializationName;
          }
        }

        if (configValues != null) {
          // If the property exists under its correct name, use this.
          if (configValues.containsKey(name)) {
            setField(field, decrypt(configValues.get(name)));
          } else {
            // Try all alternative names in the order in which they are annotated.
            if (field.isAnnotationPresent(JsonName.class)) {
              final JsonName[] jsonNames = field.getAnnotationsByType(JsonName.class);
              for (final JsonName jsonName : jsonNames) {
                if (configValues.containsKey(jsonName.value())) {
                  setField(field, decrypt(configValues.get(jsonName.value())));
                  break;
                }
              }
            }
          }
        }
        if (getEnv != null) {
          if (prefix != null) {
            setFromEnv(sb, prefixLen, field, name, true, getEnv);
          }
          if (field.isAnnotationPresent(EnvName.class)) {
            final EnvName[] envNames = field.getAnnotationsByType(EnvName.class);
            for (final EnvName envName : envNames) {
              setFromEnv(sb, prefixLen, field, envName.value(), envName.prefix(), getEnv);
            }
          }
          // If the field is not annotated and no prefix is defined,
          // then we do not read the value from the environment!
        }
      }
      theClass = theClass.getSuperclass();
    } while (theClass != null);
    //noinspection unchecked
    return (SELF) this;
  }

  /**
   * Converts the current configuration into a map. This only serializes properties annotated with {@link JsonProperty}.
   *
   * @return the current configuration as map.
   */
  public @NotNull Map<String, Object> toMap() {
    final LinkedHashMap<String, Object> map = new LinkedHashMap<>();
    final String keyId = keyId();
    Class<?> theClass = getClass();
    do {
      for (final Field field : theClass.getDeclaredFields()) {
        String name = field.getName();
        if (!field.isAnnotationPresent(JsonProperty.class)) {
          continue;
        }
        final String serializationName = field.getAnnotation(JsonProperty.class).value();
        if (serializationName != null && serializationName.length() > 0) {
          name = serializationName;
        }
        try {
          // As we go from top most class to bottom class, lower (parent) classes should not override higher classes.
          if (!map.containsKey(name)) {
            Object value = field.get(this);
            if (keyId != null && value instanceof String && field.isAnnotationPresent(JsonEncrypt.class)) {
              try {
                value = ConfigCrypt.encryptString((String) value, keyId);
              } catch (CryptoException e) {
                error("Failed to encrypt secret", e);
              }
            }
            map.put(name, value);
          }
        } catch (IllegalAccessException e) {
          info("Failed to serialize field " + field.getName() + ", access denied, skip this while saving configuration");
        }
      }
      theClass = theClass.getSuperclass();
    } while (theClass != null);
    return map;
  }

  private void setFromEnv(
      @Nonnull StringBuilder sb,
      int prefixLen,
      @Nonnull Field field,
      @Nonnull String name,
      boolean withPrefix,
      @Nonnull Function<String, String> getEnv) {
    sb.setLength(prefixLen);
    for (int i = 0; i < name.length(); i++) {
      sb.append(Character.toUpperCase(name.charAt(i)));
    }
    final String envName = withPrefix ? sb.toString() : sb.substring(prefixLen, sb.length());
    final String envValue = getEnv.apply(envName);
    if (envValue != null) {
      setField(field, decrypt(envValue));
    }
  }

  private void setField(@Nonnull Field field, @Nullable Object value) {
    if (value instanceof String) {
      value = nullable((String) value);
    }
    try {
      final Class<?> fieldType = field.getType();
      if (fieldType == int.class) {
        if (value instanceof String) {
          final int intValue = Integer.parseInt((String) value, 10);
          field.setInt(this, intValue);
        } else if (value instanceof Number) {
          field.setInt(this, ((Number) value).intValue());
        }
      } else if (fieldType == long.class) {
        if (value instanceof String) {
          final long longValue = Long.parseLong((String) value, 10);
          field.setLong(this, longValue);
        } else if (value instanceof Number) {
          field.setLong(this, ((Number) value).longValue());
        }
      } else if (fieldType == double.class) {
        if (value instanceof String) {
          final double doubleValue = Double.parseDouble((String) value);
          field.setDouble(this, doubleValue);
        } else if (value instanceof Number) {
          field.setDouble(this, ((Number) value).doubleValue());
        }
      } else if (fieldType == String.class) {
        if (value == null) {
          field.set(this, null);
        } else if (value instanceof String) {
          field.set(this, value);
        } else if (value instanceof Number) {
          field.set(this, value.toString());
        }
      } else if (fieldType == boolean.class) {
        if (value instanceof String) {
          final String stringValue = (String) value;
          final boolean boolValue =
              !"false".equalsIgnoreCase(stringValue) && !"null".equalsIgnoreCase(stringValue);
          field.setBoolean(this, boolValue);
        } else if (value instanceof Number) {
          field.setBoolean(this, ((Number) value).intValue() != 0);
        } else if (value instanceof Boolean) {
          field.setBoolean(this, (Boolean) value);
        }
      }
    } catch (NullPointerException | NumberFormatException | IllegalAccessException ignore) {
    }
  }
}
