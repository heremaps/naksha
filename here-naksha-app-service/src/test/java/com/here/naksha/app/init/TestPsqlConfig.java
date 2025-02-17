package com.here.naksha.app.init;

import static com.here.naksha.lib.core.exceptions.UncheckedException.unchecked;

import com.here.naksha.lib.core.models.payload.events.QueryParameterList;
import com.here.naksha.lib.core.util.IoHelp;
import com.here.naksha.lib.core.util.IoHelp.LoadedBytes;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import naksha.model.NakshaVersion;
import naksha.psql.PgStorage;
import org.jetbrains.annotations.NotNull;

/**
 * A simple configuration object created from a URL of the following format:
 * <pre>{@code
 * jdbc:postgresql://{HOST}[:{PORT}]/{DB}
 *   ?user={USER}
 *   &password={PASSWORD}
 *   &id={STORAGE-ID}
 *   &schema={SCHEMA}
 *   &app={APPLICATION-NAME}
 *   [&readOnly[=true|false]]
 * }</pre>. All parameters, except for <i>readOnly</i> are mandatory.
 */
public class TestPsqlConfig {

  /**
   * Reads the configuration from a configuration file from user home directory ({@code ~/.config/naksha/filename}) or from the environment
   * variable, if none is possible, a default localhost configuration is used.
   *
   * @param filename     The filename to search for in {@code ~/.config/naksha/}.
   * @param envName      The environment variable to check.
   * @param sharedSchema The shared schema, when using the shared environment variable {@code TEST_NAKSHA_PSQL_URL}.
   * @return the PSQL storage configuration.
   */
  @SuppressWarnings("SameParameterValue")
  public static @NotNull TestPsqlConfig configFromFileOrEnv(
      @NotNull String filename, @NotNull String envName, @NotNull String sharedSchema) {
    try {
      final LoadedBytes loadedBytes = IoHelp.readBytesFromHomeOrResource(filename, true, "naksha");
      final byte[] bytes = loadedBytes.getBytes();
      String url = new String(bytes, StandardCharsets.UTF_8);
      if (url.startsWith("jdbc:postgresql://")) {
        return new TestPsqlConfig(url).withStorageId(PgStorage.ADMIN_STORAGE_ID);
      }
    } catch (Exception ignore) {
    }
    String url = System.getenv(envName);
    if (url != null && url.startsWith("jdbc:postgresql://")) {
      return new TestPsqlConfig(url).withStorageId(PgStorage.ADMIN_STORAGE_ID);
    }
    url = System.getenv("TEST_NAKSHA_PSQL_URL");
    if (url != null && url.startsWith("jdbc:postgresql://")) {
      return new TestPsqlConfig(url)
          .withStorageId(PgStorage.ADMIN_STORAGE_ID)
          .withSchema(sharedSchema);
    }

    String password = System.getenv("TEST_NAKSHA_PSQL_PASS");
    if (password == null || password.isBlank()) {
      password = "password";
    }
    return new TestPsqlConfig("jdbc:postgresql://localhost:5432/postgres?user=postgres&password=" + password
                              + "&schema=" + sharedSchema
                              + "&app=" + "Naksha/v" + NakshaVersion.latest
                              + "&id=" + PgStorage.ADMIN_STORAGE_ID);
  }

  public TestPsqlConfig(@NotNull String url) {
    parseUrl(url);
  }

  public final @NotNull TestPsqlConfig parseUrl(@NotNull String postgresUrl) {
    try {
      // Syntax: jdbc:postgresql://host[:port]/db
      final URI root = new URI(postgresUrl);
      if (!"jdbc".equalsIgnoreCase(root.getScheme())) {
        throw new URISyntaxException(
            postgresUrl, "Expect scheme to be 'jdbc', but found: '" + root.getScheme() + "'");
      }
      final URI uri = new URI(root.getSchemeSpecificPart());
      if (!"postgresql".equalsIgnoreCase(uri.getScheme())) {
        throw new URISyntaxException(
            postgresUrl,
            "Expect scheme of specific part to be 'postgresql', but found: '" + uri.getScheme() + "'");
      }
      String path = uri.getPath();
      while (path != null && path.length() > 0 && path.charAt(0) == '/') {
        path = path.substring(1);
      }
      if (path == null || path.length() == 0) {
        throw new URISyntaxException(postgresUrl, "Missing database name as path");
      }
      if (path.contains("/")) {
        throw new URISyntaxException(postgresUrl, "Invalid database name: " + path);
      }

      final String host;
      if (uri.getHost() != null) {
        host = uri.getHost();
        if (host.length() == 0) {
          throw new URISyntaxException(postgresUrl, "Hostname is empty");
        }
      } else {
        throw new URISyntaxException(postgresUrl, "Hostname is empty");
      }

      final int port;
      if (uri.getPort() >= 0) {
        port = uri.getPort();
      } else {
        port = 5432;
      }
      setBasics(host, port, path);

      final String query = uri.getQuery();
      if (query != null && query.length() > 0) {
        setParams(new QueryParameterList(query));
      }
      return this;
    } catch (Throwable t) {
      throw unchecked(t);
    }
  }

  private void setBasics(@NotNull String host, int port, @NotNull String db) {
    this.host = host;
    this.port = port;
    this.db = db;
  }

  private void setParams(@NotNull QueryParameterList params) {
    if (params.getValue("user") instanceof String) {
      user = (String) params.getValue("user");
    } else {
      throw new IllegalArgumentException("The URL must have a parameter '&user'");
    }
    if (params.getValue("password") instanceof String) {
      password = (String) params.getValue("password");
    } else {
      throw new IllegalArgumentException("The URL must have a parameter '&password'");
    }
    if (params.getValue("appName") instanceof String) {
      appName = (String) params.getValue("appName");
    } else if (params.getValue("appname") instanceof String) {
      appName = (String) params.getValue("appname");
    } else if (params.getValue("app_name") instanceof String) {
      appName = (String) params.getValue("app_name");
    } else if (params.getValue("app") instanceof String) {
      appName = (String) params.getValue("app");
    } else {
      throw new IllegalArgumentException("The URL must have a parameter '&app'");
    }
    if (params.getValue("schema") instanceof String) {
      schema = (String) params.getValue("schema");
    } else {
      throw new IllegalArgumentException("The URL must have a parameter '&schema'");
    }
    if (params.getValue("id") instanceof String) {
      storageId = (String) params.getValue("id");
    } else if (params.getValue("storageId") instanceof String) {
      storageId = (String) params.getValue("storageId");
    } else if (params.getValue("storageid") instanceof String) {
      storageId = (String) params.getValue("storageid");
    } else if (params.getValue("storage_id") instanceof String) {
      storageId = (String) params.getValue("storage_id");
    } else {
      throw new IllegalArgumentException("The URL must have a parameter '&id'");
    }
    Object raw = params.getValue("readOnly");
    if (raw == null) {
      raw = params.getValue("readonly");
    }
    if (raw instanceof Boolean) {
      readOnly = (Boolean) raw;
    } else if (raw instanceof String) {
      readOnly = !"false".equalsIgnoreCase((String) raw);
    } else {
      readOnly = false;
    }
  }

  private String url;

  public @NotNull String url() {
    if (url == null) {
      updateUrl();
    }
    return url;
  }

  private String host;

  public @NotNull String host() {
    return host;
  }

  public @NotNull TestPsqlConfig withHost(@NotNull String host) {
    this.url = null;
    this.host = host;
    return this;
  }

  private int port;

  public int port() {
    return port;
  }

  public @NotNull TestPsqlConfig withPort(int port) {
    if (port < 0 || port > 65535) {
      throw new IllegalArgumentException("port must be between 0 and 65535, but is " + port);
    }
    this.url = null;
    this.port = port;
    return this;
  }

  private String db;

  public @NotNull String db() {
    return db;
  }

  public @NotNull TestPsqlConfig withDb(@NotNull String db) {
    this.url = null;
    this.db = db;
    return this;
  }

  private String user;

  public @NotNull String user() {
    return user;
  }

  public @NotNull TestPsqlConfig withUser(@NotNull String user) {
    this.url = null;
    this.user = user;
    return this;
  }

  private String password;

  public @NotNull String password() {
    return password;
  }

  public @NotNull TestPsqlConfig withPassword(@NotNull String password) {
    this.url = null;
    this.password = password;
    return this;
  }

  private String storageId;

  public @NotNull String storageId() {
    return storageId;
  }

  public @NotNull TestPsqlConfig withStorageId(@NotNull String storageId) {
    this.url = null;
    this.storageId = storageId;
    return this;
  }

  private String appName;

  public @NotNull String appName() {
    return appName;
  }

  public @NotNull TestPsqlConfig withAppName(@NotNull String appName) {
    this.url = null;
    this.appName = appName;
    return this;
  }

  private String schema;

  public @NotNull String schema() {
    return schema;
  }

  public @NotNull TestPsqlConfig withSchema(@NotNull String schema) {
    this.url = null;
    this.schema = schema;
    return this;
  }

  private boolean readOnly;

  public boolean readOnly() {
    return readOnly;
  }

  public @NotNull TestPsqlConfig withReadOnly(boolean readOnly) {
    this.url = null;
    this.readOnly = readOnly;
    return this;
  }

  void updateUrl() {
    final StringBuilder sb = new StringBuilder();
    sb.append("jdbc:postgresql://");
    sb.append(host);
    if (port != 5432 && port != 0) {
      sb.append(':');
      sb.append(port);
    }
    sb.append('/').append(db);
    sb.append("?schema=").append(schema);
    sb.append("&id=").append(storageId);
    sb.append("&app=").append(appName);
    sb.append("&user=").append(user);
    sb.append("&password=").append(password);
    url = sb.toString();
  }
}