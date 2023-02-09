package com.here.xyz.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.here.xyz.util.ARN;
import com.here.xyz.util.EnvName;
import com.here.xyz.util.JsonConfigFile;
import com.here.xyz.util.JsonFilename;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The bootstrap configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonFilename("config.json")
public class XyzConfig extends JsonConfigFile<XyzConfig> {

  public XyzConfig() {
  }

  @javax.annotation.Nullable
  protected String envPrefix() {
    return "XYZ_";
  }

  /**
   * Returns the current configuration.
   *
   * @return the config.
   * @throws IllegalStateException if loading the configuration failed.
   */
  public static @NotNull XyzConfig get() throws IllegalStateException {
    return JsonConfigFile.get(XyzConfig.class);
  }

  /**
   * True, if enabled debugging mode; false otherwise.
   */
  @JsonProperty
  public boolean DEBUG;

  /**
   * The HTTP hostname.
   */
  @JsonProperty
  public @NotNull String HTTP_HOST = "localhost";

  /**
   * The HTTP port to listen for.
   */
  @JsonProperty
  public int HTTP_PORT = 8080;

  /**
   * Hub-Endpoint
   */
  @JsonProperty
  public @NotNull String HTTP_ENDPOINT = "http://" + HTTP_HOST + ":" + HTTP_PORT + "/hub";

  /**
   * The HTTP hostname.
   */
  @JsonProperty
  public @NotNull String PUBLIC_HTTP_HOST = "localhost";

  /**
   * The public HTTP port, should be configured if this service is behind some load-balancer or firewall that re-write the port.
   */
  @JsonProperty
  public int PUBLIC_HTTP_PORT = 8080;

  /**
   * The public endpoint.
   */
  @JsonProperty
  public @NotNull String PUBLIC_HTTP_ENDPOINT = "http://" + PUBLIC_HTTP_HOST + ":" + HTTP_PORT + "/hub";

  /**
   * The user-agent that should be sent by the Web-Client used by XYZ-hub, and its components.
   */
  @JsonProperty
  public @NotNull String USER_AGENT = "XYZ-Hub";

  /**
   * The AWS region this service is running in. Value is <code>null</code> if not running in AWS.
   */
  @JsonProperty
  public @Nullable String AWS_REGION;

  /**
   * An identifier for the service environment.
   */
  @JsonProperty
  @EnvName("ENV")
  public @NotNull String ENVIRONMENT_NAME = "local";

  /**
   * True, if the vertx metrics should be enabled; false otherwise.
   */
  @JsonProperty
  public boolean START_METRICS;

  /**
   * Whether to publish custom service metrics like JVM memory utilization or Major GC count.
   */
  @JsonProperty
  public boolean PUBLISH_METRICS;

  /**
   * The LOG4J configuration file for console logging.
   */
  @JsonIgnore
  public static final String LOG_CONFIG_CONSOLE = "log4j2-console-plain.json";

  /**
   * The LOG4J configuration file for console logging of JSON.
   */
  @JsonIgnore
  public static final String LOG_CONFIG_CONSOLE_JSON = "log4j2-console-json.json";

  @JsonProperty
  protected @NotNull String LOG_CONFIG = LOG_CONFIG_CONSOLE;

  /**
   * Returns the filename of the log4j2 JSON configuration.
   *
   * @return the filename of the log4j2 JSON configuration.
   */
  public @NotNull String LOG_CONFIG() {
    return nullable(LOG_CONFIG) == null ? LOG_CONFIG_CONSOLE : LOG_CONFIG_CONSOLE_JSON;
  }

  /**
   * The amount of Vertx workers to use.
   */
  @JsonProperty
  protected int VERTX_WORKER_POOL_SIZE;

  public int vertxWorkerPoolSize() {
    // By default, we use 100 threads per processor, which means in the worst case each thread has 10ms or runtime.
    return VERTX_WORKER_POOL_SIZE > 0 ? VERTX_WORKER_POOL_SIZE : Runtime.getRuntime().availableProcessors() * 100;
  }

  /**
   * Whether to activate pipelining for the HTTP client of the service.
   */
  @JsonProperty
  public boolean HTTP_CLIENT_PIPELINING;

  /**
   * Whether to activate TCP keepalive for the HTTP client of the service.
   */
  @JsonProperty
  public boolean HTTP_CLIENT_TCP_KEEPALIVE = true;

  /**
   * The idle connection timeout in seconds for the HTTP client of the service. Setting it to 0 will make the connections not timing out at
   * all.
   */
  @JsonProperty
  public int HTTP_CLIENT_IDLE_TIMEOUT = 120;

  /**
   * If the bootstrap code should start the Hub-Verticle, which will expose the official XYZ-Hub API.
   */
  @JsonProperty
  public boolean DEPLOY_XYZ_HUB_REST_VERTICLE = true;

  /**
   * If the bootstrap code should start the Http-Connector verticle, which will expose an end-point for the PSQL connector.
   */
  @JsonProperty
  public boolean DEPLOY_PSQL_HTTP_CONNECTOR_VERTICLE = true;

  /**
   * Initial connection-pool size.
   */
  @JsonProperty
  public int DB_INITIAL_POOL_SIZE = 5;

  /**
   * Minimal size of the connection-pool.
   */
  @JsonProperty
  public int DB_MIN_POOL_SIZE = 1;

  /**
   * Maximum amount of connection to keep in the pool.
   */
  @JsonProperty
  public int DB_MAX_POOL_SIZE = 50;

  /**
   * How many times the JDBC client will try to get a new connection from the database before giving up.
   */
  @JsonProperty
  public int DB_ACQUIRE_RETRY_ATTEMPTS = 10;

  /**
   * How many connections should be created, if the pool runs out of available connections and new ones need to be added. This will not go
   * beyond the {@link #DB_MAX_POOL_SIZE}.
   */
  @JsonProperty
  public int DB_ACQUIRE_INCREMENT = 1;

  /**
   * Max Time to wait for a connection from the pool in seconds.
   */
  @JsonProperty
  public int DB_CHECKOUT_TIMEOUT = 10;

  /**
   * Test the connection received from the pool before using it.
   */
  @JsonProperty
  public boolean DB_TEST_CONNECTION_ON_CHECKOUT = true;

  /**
   * Statement timeout in seconds.
   */
  @JsonProperty
  public int DB_STATEMENT_TIMEOUT_IN_S = 10;

  /**
   * Temporary needed for migration phase
   */
  @JsonProperty
  public boolean JOB_OLD_DATABASE_LAYOUT;

  @JsonProperty
  public String ECPS_PASSPHRASE = "local";

  /**
   * Maximum amount of parallel running maintenance tasks.
   */
  @JsonProperty
  public int MAX_CONCURRENT_MAINTENANCE_TASKS = 1;

  /**
   * Defines the time threshold in which a maintenance should be finished. If reached, a warning gets logged.
   */
  @JsonProperty
  public int MISSING_MAINTENANCE_WARNING_IN_HR = 12;

  /**
   * If the "Burst and Update" thread should be started.
   */
  @JsonProperty
  public boolean START_BURST_AND_UPDATE_THREAD = false;

  /**
   * If the "Warmup" thread for the remove functions should be started.
   */
  @JsonProperty
  public boolean START_WARMUP_REMOTE_FUNCTION_THREAD = false;

  /**
   * The global maximum amount of HTTP client connections.
   */
  @JsonProperty
  public int MAX_GLOBAL_HTTP_CLIENT_CONNECTIONS = 10_000;
  // TODO: This value is miss-leading, because it is used used for HTTP and HTTP/2 connections, therefore between this and twice the amount!

  /**
   * Size of the off-heap cache in megabytes.
   */
  @JsonProperty
  public int OFF_HEAP_CACHE_SIZE_MB = 1024;

  /**
   * The initial amount of service instances.
   */
  @JsonProperty
  public int INSTANCE_COUNT = 1;
  // TODO: Why do we need this, I think this information is totally useless, we should use Hazelcast to cluster!

  /**
   * The S3 Bucket which could be used by connectors with transfer limitations to relocate responses.
   */
  @JsonProperty
  public String XYZ_HUB_S3_BUCKET;

  /**
   * The redis host.
   */
  @Deprecated
  @JsonProperty
  public String XYZ_HUB_REDIS_HOST;

  /**
   * The redis port.
   */
  @Deprecated
  @JsonProperty
  public int XYZ_HUB_REDIS_PORT = 6379;

  /**
   * The redis connection string.
   */
  @JsonProperty
  protected String XYZ_HUB_REDIS_URI;

  /**
   * The redis auth token.
   */
  @JsonProperty
  public String XYZ_HUB_REDIS_AUTH_TOKEN;

  /**
   * Adds backward-compatibility for the deprecated environment variables XYZ_HUB_REDIS_HOST & XYZ_HUB_REDIS_PORT.
   *
   * @return the URI of Redis.
   */
  @JsonIgnore
  public @Nullable String getRedisUri() {
    String redis_url = nullable(XYZ_HUB_REDIS_URI);
    if (redis_url == null) {
      final String redis_host = nullable(XYZ_HUB_REDIS_HOST);
      if (redis_host != null) {
        final String redis_protocol = nullable(XYZ_HUB_REDIS_AUTH_TOKEN) != null ? "rediss" : "redis";
        redis_url = redis_protocol + "://" + redis_host + ":" + XYZ_HUB_REDIS_PORT;
      }
    }
    return redis_url;
  }

  /**
   * The urls of remote hub services, separated by a semicolon ';'.
   */
  @JsonProperty
  public String XYZ_HUB_REMOTE_SERVICE_URLS;
  private List<String> hubRemoteServiceUrls;

  public List<String> getHubRemoteServiceUrls() {
    if (hubRemoteServiceUrls == null) {
      hubRemoteServiceUrls = XYZ_HUB_REMOTE_SERVICE_URLS == null ? null : Arrays.asList(XYZ_HUB_REMOTE_SERVICE_URLS.split(";"));
    }
    return hubRemoteServiceUrls;
  }

  /**
   * Authorization via JWT token.
   */
  @SuppressWarnings("unused")
  public static final String XYZ_HUB_AUTH_JWT = "JWT";

  /**
   * Authorization via DUMMY token (no auth).
   */
  public static final String XYZ_HUB_AUTH_DUMMY = "DUMMY";

  /**
   * The authorization type.
   */
  @JsonProperty
  public @NotNull String XYZ_HUB_AUTH = XYZ_HUB_AUTH_DUMMY;

  /**
   * The public key used for verifying the signature of the JWT tokens.
   */
  @JsonProperty
  public @Nullable String JWT_PUB_KEY;

  /**
   * Adds backward-compatibility for public keys without header & footer.
   *
   * @return The JWT public key; if any.
   */
  //TODO: Remove this workaround after the deprecation period
  @Nullable
  @JsonIgnore
  public String getJwtPubKey() {
    String jwtPubKey = JWT_PUB_KEY;
    if (jwtPubKey != null) {
      if (!jwtPubKey.startsWith("-----")) {
        jwtPubKey = "-----BEGIN PUBLIC KEY-----\n" + jwtPubKey;
      }
      if (!jwtPubKey.endsWith("-----")) {
        jwtPubKey = jwtPubKey + "\n-----END PUBLIC KEY-----";
      }
    }
    return jwtPubKey;
  }

  /**
   * If set to true, the connectors configuration will be populated with connectors defined in connectors.json.
   */
  @JsonProperty
  public boolean INSERT_LOCAL_CONNECTORS = true;

  /**
   * If true HERE Tiles are get handled as Base 4 encoded. Default is false (Base 10).
   */
  @JsonProperty
  public boolean USE_BASE_4_H_TILES;

  /**
   * The ID of the default storage.
   */
  @JsonProperty
  public @NotNull String DEFAULT_STORAGE_ID = "psql";

  /**
   * The PostgreSQL URL.
   */
  @JsonProperty
  public @NotNull String STORAGE_DB_URL;

  /**
   * The database user.
   */
  @JsonProperty
  public @NotNull String STORAGE_DB_USER;

  /**
   * The database password.
   */
  @JsonProperty
  public @NotNull String STORAGE_DB_PASSWORD;

  /**
   * The http connector host (by default run as monolith).
   */
  @JsonProperty
  public @NotNull String PSQL_HTTP_CONNECTOR_HOST = HTTP_HOST;

  /**
   * The http connector port (by default run as monolith).
   */
  @JsonProperty
  public int PSQL_HTTP_CONNECTOR_PORT = HTTP_PORT;

  /**
   * The ARN of the space table in DynamoDB.
   */
  @JsonProperty
  public String SPACES_DYNAMODB_TABLE_ARN;

  /**
   * The ARN of the connectors table in DynamoDB.
   */
  @JsonProperty
  public String CONNECTORS_DYNAMODB_TABLE_ARN;

  /**
   * The ARN of the packages table in DynamoDB.
   */
  @JsonProperty
  public String PACKAGES_DYNAMODB_TABLE_ARN;

  /**
   * The default broker to be use.
   */
  @JsonProperty
  public String DEFAULT_MESSAGE_BROKER;

  /**
   * The ARN of the subscriptions table in DynamoDB.
   */
  @JsonProperty
  public String SUBSCRIPTIONS_DYNAMODB_TABLE_ARN;

  /**
   * The ARN of the admin message topic.
   */
  @JsonProperty
  public ARN ADMIN_MESSAGE_TOPIC_ARN;

  /**
   * The JWT token used for sending admin messages.
   */
  @JsonProperty
  public String ADMIN_MESSAGE_JWT;

  /**
   * The total size assigned for remote functions queues.
   */
  @JsonProperty
  public int GLOBAL_MAX_QUEUE_SIZE = 1024; //MB

  /**
   * The maximum timeout for remote function requests in seconds. Can be overridden by connector configuration, but only decreased.
   */
  @JsonProperty
  public int REMOTE_FUNCTION_MAX_REQUEST_TIMEOUT_MS = (int) TimeUnit.MINUTES.toMillis(5);

  /**
   * The maximum amount of RemoteFunction connections to be opened by this node.
   */
  @JsonProperty
  public int REMOTE_FUNCTION_MAX_CONNECTIONS = 1024;

  /**
   * A value between 0 and 1 defining a threshold as percentage of utilized RemoteFunction max-connections after which to start prioritizing
   * more important connectors over less important ones.
   *
   * @see #REMOTE_FUNCTION_MAX_CONNECTIONS
   */
  @JsonProperty
  public float REMOTE_FUNCTION_CONNECTION_HIGH_UTILIZATION_THRESHOLD = 0.9f;

  /**
   * The remote function pool ID to be used to select the according remote functions for this Service environment.
   */
  @JsonProperty
  public String REMOTE_FUNCTION_POOL_ID;

  /**
   * The web root for serving static resources from the file system.
   */
  @JsonProperty
  public String FS_WEB_ROOT;

  /**
   * The name of the upload limit header.
   */
  @JsonProperty
  public String UPLOAD_LIMIT_HEADER_NAME = "X-Upload-Content-Length-Limit";

  /**
   * The code which gets returned if UPLOAD_LIMIT is reached
   */
  @JsonProperty
  public int UPLOAD_LIMIT_REACHED_HTTP_CODE;

  /**
   * The message which gets returned if UPLOAD_LIMIT is reached
   */
  @JsonProperty
  public String UPLOAD_LIMIT_REACHED_MESSAGE;

  /**
   * The name of the health check header to instruct for additional health status information.
   */
  @JsonProperty
  public String HEALTH_CHECK_HEADER_NAME;

  /**
   * The value of the health check header to instruct for additional health status information.
   */
  @JsonProperty
  public String HEALTH_CHECK_HEADER_VALUE;

  /**
   * The topic ARN for Space modification notifications. If no value is provided no notifications will be sent.
   */
  @JsonProperty
  public String MSE_NOTIFICATION_TOPIC;

  /**
   * The maximum size of an event transiting between connector -> service -> client. Validation is only applied when
   * MAX_SERVICE_RESPONSE_SIZE is bigger than zero.
   *
   * @deprecated Use instead MAX_UNCOMPRESSED_RESPONSE_SIZE
   */
  @JsonProperty
  public long MAX_SERVICE_RESPONSE_SIZE = Long.MAX_VALUE;

  /**
   * The maximum uncompressed request size in bytes supported on API calls. If uncompressed request size is bigger than
   * MAX_UNCOMPRESSED_REQUEST_SIZE, an error with status code 413 will be sent.
   */
  @JsonProperty
  public long MAX_UNCOMPRESSED_REQUEST_SIZE = 1024 * 1024L;

  /**
   * The maximum uncompressed response size in bytes supported on API calls. If uncompressed response size is bigger than
   * MAX_UNCOMPRESSED_RESPONSE_SIZE, an error with status code 513 will be sent.
   */
  @JsonProperty
  public long MAX_UNCOMPRESSED_RESPONSE_SIZE = 1024 * 1024L;

  /**
   * The maximum http response size in bytes supported on API calls. If response size is bigger than MAX_HTTP_RESPONSE_SIZE, an error with
   * status code 513 will be sent. Validation is only applied when MAX_HTTP_RESPONSE_SIZE is bigger than zero.
   *
   * @deprecated Use instead MAX_UNCOMPRESSED_RESPONSE_SIZE
   */
  @JsonProperty
  public long MAX_HTTP_RESPONSE_SIZE = Long.MAX_VALUE;

  /**
   * List of fields, separated by comma, which are optional on feature's namespace property.
   */
  @JsonIgnore
  private List<String> FEATURE_NAMESPACE_OPTIONAL_FIELDS = Collections.emptyList();

  @JsonIgnore
  private Map<String, Object> FEATURE_NAMESPACE_OPTIONAL_FIELDS_MAP;

  /**
   * When set, modifies the Stream-Info header name to the value specified.
   */
  @JsonProperty
  public @NotNull String CUSTOM_STREAM_INFO_HEADER_NAME;

  /**
   * Whether the service should use InstanceProviderCredentialsProfile with cached credential when utilizing AWS clients.
   */
  @JsonProperty
  public boolean USE_AWS_INSTANCE_CREDENTIALS_WITH_REFRESH;

  public boolean containsFeatureNamespaceOptionalField(String field) {
    if (FEATURE_NAMESPACE_OPTIONAL_FIELDS_MAP == null) {
      FEATURE_NAMESPACE_OPTIONAL_FIELDS_MAP = new HashMap<String, Object>() {{
        FEATURE_NAMESPACE_OPTIONAL_FIELDS.forEach(k -> put(k, null));
      }};
    }

    return FEATURE_NAMESPACE_OPTIONAL_FIELDS_MAP.containsKey(field);
  }

  /**
   * Global limit for the maximum amount of versions to keep per space.
   */
  @JsonProperty
  public long MAX_VERSIONS_TO_KEEP = 1_000_000_000;

  /**
   * Flag indicating whether the author should be retrieved from the custom header Author.
   */
  @JsonProperty
  public boolean USE_AUTHOR_FROM_HEADER; // = false

  /**
   * Endpoint, which includes maintenance and Job-API.
   */
  @JsonProperty
  public @NotNull String HTTP_CONNECTOR_ENDPOINT = "http://" + HTTP_HOST + ":" + HTTP_PORT + "/psql";

  /**
   * If set to true, the service responses will include headers with information about the decompressed size of the request and response
   * payloads.
   */
  @JsonProperty
  public boolean INCLUDE_HEADERS_FOR_DECOMPRESSED_IO_SIZE = true;

  /**
   * The name of the header for reporting the decompressed size of the response payload.
   */
  @JsonProperty
  public @NotNull String DECOMPRESSED_INPUT_SIZE_HEADER_NAME = "X-Decompressed-Input-Size";

  /**
   * The name of the header for reporting the decompressed size of the response payload.
   */
  @JsonProperty
  public @NotNull String DECOMPRESSED_OUTPUT_SIZE_HEADER_NAME = "X-Decompressed-Output-Size";

  /**
   * ECPS_PHRASE of Default Connector
   */
  @JsonProperty
  public @Nullable String ECPS_PHRASE;

  /**
   * ARN of DynamoDB table for jobs; if being {@code null}, then using JDBC.
   */
  @JsonProperty
  public @Nullable String JOBS_DYNAMODB_TABLE_ARN;

  /**
   * S3 Bucket for imports/exports.
   */
  @JsonProperty
  public @NotNull String JOBS_S3_BUCKET = "wikvaya-private";

  /**
   * S3 Bucket for imports/exports
   */
  @JsonProperty
  public @NotNull String JOBS_S3_BUCKET_REGION = "us-east-1";

  /**
   * Set interval for JobQueue processing.
   */
  @JsonProperty
  public int JOB_CHECK_QUEUE_INTERVAL_SECONDS = 10;

  /**
   * Maximum amount of concurrently running jobs.
   */
  @JsonProperty
  public int JOB_MAX_RUNNING_JOBS = 2;

  /**
   * List of "connectorId:cloudWatchDBInstanceIdentifier:MaxCapacityUnits", separated by a comma.
   */
  @JsonProperty
  protected @Nullable String JOB_SUPPORTED_RDS = "psql:dbIdentifier:0";

  @JsonIgnore
  private List<String> JOB_SUPPORTED_RDS_cache;

  @JsonIgnore
  public @NotNull List<@NotNull String> JOB_SUPPORTED_RDS() {
    if (JOB_SUPPORTED_RDS_cache == null) {
      final List<String> cache = new ArrayList<>();
      if (JOB_SUPPORTED_RDS != null) {
        final String[] values = JOB_SUPPORTED_RDS.split(",");
        for (final String value : values) {
          cache.add(value.trim());
        }
      }
      JOB_SUPPORTED_RDS_cache = cache;
    }
    return JOB_SUPPORTED_RDS_cache;
  }

  /**
   * RDS maximum ACUs.
   */
  @JsonProperty
  public int JOB_MAX_RDS_CAPACITY = 30;

  /**
   * RDS maximum CPU Load in percentage.
   */
  @JsonProperty
  public int JOB_MAX_RDS_CPU_LOAD = 20;

  /**
   * RDS maximum allowed import bytes.
   */
  @JsonProperty
  public long JOB_MAX_RDS_INFLIGHT_IMPORT_BYTES = 30 * 1024 * 1024 * 1024L;

  /**
   * RDS maximum allowed idx creations in parallel
   */
  @JsonProperty
  public int JOB_MAX_RDS_MAX_RUNNING_IDX_CREATIONS = 10;

  /**
   * RDS maximum allowed imports in parallel
   */
  @JsonProperty
  public int JOB_MAX_RDS_MAX_RUNNING_IMPORTS = 2;

  /**
   * RDS maximum allowed imports in parallel
   */
  @JsonProperty
  public long JOB_DYNAMO_EXP_IN_DAYS = 10;

}