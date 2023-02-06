package com.here.xyz.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.here.xyz.util.ARN;
import com.here.xyz.util.JsonConfigFile;
import com.here.xyz.util.JsonFilename;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The service configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonFilename("config.json")
public class ServiceConfig extends JsonConfigFile<ServiceConfig> {

  public ServiceConfig() {
    super(null);
  }

  public ServiceConfig(@NotNull String filename) {
    super(filename);
  }

  /**
   * Returns the current configuration.
   *
   * @return the config.
   * @throws IllegalStateException if loading the configuration failed.
   */
  public static @NotNull ServiceConfig get() throws IllegalStateException {
    return JsonConfigFile.get(ServiceConfig.class);
  }

  /**
   * The global maximum number of http client connections.
   */
  @JsonProperty
  public int MAX_GLOBAL_HTTP_CLIENT_CONNECTIONS;

  /**
   * Size of the off-heap cache in megabytes.
   */
  @JsonProperty
  public int OFF_HEAP_CACHE_SIZE_MB;

  /**
   * True, if enabled debugging; false otherwise.
   */
  @JsonProperty
  public boolean DEBUG;

  /**
   * True, if the vertx metrics should be enabled; false otherwise.
   */
  @JsonProperty
  public boolean START_METRICS;

  @JsonProperty
  public boolean START_BURST_AND_UPDATE_THREAD;

  @JsonProperty
  public boolean START_WARMUP_REMOTE_FUNCTION_THREAD;

  @JsonProperty
  public boolean DEPLOY_XYZ_HUB_REST_VERTICLE;

  @JsonProperty
  public boolean DEPLOY_PSQL_HTTP_CONNECTOR_VERTICLE;

  /**
   * The LOG4J configuration file for console logging.
   */
  protected static final String LOG_CONFIG_CONSOLE = "log4j2-console-plain.json";

  /**
   * The LOG4J configuration file for console logging of JSON.
   */
  protected static final String LOG_CONFIG_CONSOLE_JSON = "log4j2-console-json.json";

  @JsonProperty
  protected String LOG_CONFIG;

  /**
   * Returns the filename of the log4j2 JSON configuration.
   *
   * @return the filename of the log4j2 JSON configuration.
   */
  public String LOG_CONFIG() {
    return nullable(LOG_CONFIG) == null ? LOG_CONFIG_CONSOLE : LOG_CONFIG_CONSOLE_JSON;
  }

  /**
   * The port of the HTTP server.
   */
  @JsonProperty
  public int HTTP_PORT;

  /**
   * The hostname.
   */
  @JsonProperty
  public String HOST_NAME;

  /**
   * The initial number of instances.
   */
  @JsonProperty
  public int INSTANCE_COUNT;

  /**
   * The S3 Bucket which could be used by connectors with transfer limitations to relocate responses.
   */
  @JsonProperty
  public String XYZ_HUB_S3_BUCKET;

  /**
   * The public endpoint.
   */
  @JsonProperty
  public String XYZ_HUB_PUBLIC_ENDPOINT;

  /**
   * The public health-check endpoint, i.e. /hub/
   */
  @JsonProperty
  public String XYZ_HUB_PUBLIC_HEALTH_ENDPOINT = "/hub/";

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
  public int XYZ_HUB_REDIS_PORT;

  /**
   * The redis connection string.
   */
  @JsonProperty
  public String XYZ_HUB_REDIS_URI;

  /**
   * The redis auth token.
   */
  @JsonProperty
  public String XYZ_HUB_REDIS_AUTH_TOKEN;

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
   * True if the {@code PsqlHttpConnectorVerticle} should be deployed; false otherwise.
   */
  @JsonProperty
  public boolean DEPLOY_PSQL_VERTICLE;

  /**
   * Adds backward-compatibility for the deprecated environment variables XYZ_HUB_REDIS_HOST & XYZ_HUB_REDIS_PORT.
   *
   * @return the URI of Redis.
   */
  //TODO: Remove this workaround after the deprecation period
  @JsonIgnore
  public @Nullable String getRedisUri() {
    if (XYZ_HUB_REDIS_HOST != null) {
      String protocol = XYZ_HUB_REDIS_AUTH_TOKEN != null ? "rediss" : "redis";
      int port = XYZ_HUB_REDIS_PORT != 0 ? XYZ_HUB_REDIS_PORT : 6379;
      return protocol + "://" + XYZ_HUB_REDIS_HOST + ":" + port;
    } else {
      return nullable(XYZ_HUB_REDIS_URI);
    }
  }

  /**
   * The urls of remote hub services, separated by semicolon ';'
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
   * The authorization type.
   */
  @JsonProperty
  public String XYZ_HUB_AUTH;

  /**
   * Authorization via JWT token.
   */
  public static final String XYZ_HUB_AUTH_JWT = "JWT";
  /**
   * Authorization via DUMMY token (no auth).
   */
  public static final String XYZ_HUB_AUTH_DUMMY = "DUMMY";

  /**
   * The public key used for verifying the signature of the JWT tokens.
   */
  @JsonProperty
  public String JWT_PUB_KEY;

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
  public boolean INSERT_LOCAL_CONNECTORS;

  /**
   * If set to true, the connectors will receive health checks. Further an unhealthy connector gets deactivated automatically if the
   * connector config does not include skipAutoDisable.
   */
  @JsonProperty
  public boolean ENABLE_CONNECTOR_HEALTH_CHECKS;

  /**
   * If true HERE Tiles are get handled as Base 4 encoded. Default is false (Base 10).
   */
  @JsonProperty
  public boolean USE_BASE_4_H_TILES;

  /**
   * The ID of the default storage.
   */
  @JsonProperty
  public String DEFAULT_STORAGE_ID = "psql";

  /**
   * The PostgreSQL URL.
   */
  @JsonProperty
  public String STORAGE_DB_URL;

  /**
   * The database user.
   */
  @JsonProperty
  public String STORAGE_DB_USER;

  /**
   * The database password.
   */
  @JsonProperty
  public String STORAGE_DB_PASSWORD;

  /**
   * The http connector host.
   */
  @JsonProperty
  public String PSQL_HTTP_CONNECTOR_HOST;

  /**
   * The http connector port.
   */
  @JsonProperty
  public int PSQL_HTTP_CONNECTOR_PORT;

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
   * The port for the admin message server.
   */
  @JsonProperty
  public int ADMIN_MESSAGE_PORT;

  /**
   * The total size assigned for remote functions queues.
   */
  @JsonProperty
  public int GLOBAL_MAX_QUEUE_SIZE; //MB

  /**
   * The default timeout for remote function requests in seconds.
   */
  @JsonProperty
  public int REMOTE_FUNCTION_REQUEST_TIMEOUT; //seconds

  /**
   * OPTIONAL: The maximum timeout for remote function requests in seconds. If not specified, the value of
   * {@link #REMOTE_FUNCTION_REQUEST_TIMEOUT} will be used.
   */
  @JsonProperty
  public int REMOTE_FUNCTION_MAX_REQUEST_TIMEOUT; //seconds

  /**
   * @return the value of {@link #REMOTE_FUNCTION_MAX_REQUEST_TIMEOUT} if specified. The value of {@link #REMOTE_FUNCTION_REQUEST_TIMEOUT}
   * otherwise.
   */
  @JsonIgnore
  public int getRemoteFunctionMaxRequestTimeout() {
    return REMOTE_FUNCTION_MAX_REQUEST_TIMEOUT > 0 ? REMOTE_FUNCTION_MAX_REQUEST_TIMEOUT : REMOTE_FUNCTION_REQUEST_TIMEOUT;
  }

  /**
   * The maximum amount of RemoteFunction connections to be opened by this node.
   */
  @JsonProperty
  public int REMOTE_FUNCTION_MAX_CONNECTIONS = 1024;

  /**
   * The amount of memory (in MB) which can be taken by incoming requests.
   */
  @JsonProperty
  public int GLOBAL_INFLIGHT_REQUEST_MEMORY_SIZE_MB;

  /**
   * A value between 0 and 1 defining a threshold as percentage of utilized RemoteFunction max-connections after which to start prioritizing
   * more important connectors over less important ones.
   *
   * @see ServiceConfig#REMOTE_FUNCTION_MAX_CONNECTIONS
   */
  @JsonProperty
  public float REMOTE_FUNCTION_CONNECTION_HIGH_UTILIZATION_THRESHOLD;

  /**
   * A value between 0 and 1 defining a threshold as percentage of utilized service memory for in-flight request after which to start
   * prioritizing more important connectors over less important ones.
   */
  @JsonProperty
  public float GLOBAL_INFLIGHT_REQUEST_MEMORY_HIGH_UTILIZATION_THRESHOLD;

  /**
   * A value between 0 and 1 defining a threshold as percentage of utilized service memory which depicts a very high utilization of the the
   * memory. The service uses that threshold to perform countermeasures to protect the service from overload.
   */
  @JsonProperty
  public float SERVICE_MEMORY_HIGH_UTILIZATION_THRESHOLD;

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
   * The name of the upload limit header
   */
  @JsonProperty
  public String UPLOAD_LIMIT_HEADER_NAME;

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
   * An identifier for the service environment.
   */
  @JsonProperty
  public String ENVIRONMENT_NAME;

  /**
   * Whether to publish custom service metrics like JVM memory utilization or Major GC count.
   */
  @JsonProperty
  public boolean PUBLISH_METRICS;

  /**
   * The AWS region this service is running in. Value is <code>null</code> if not running in AWS.
   */
  @JsonProperty
  public String AWS_REGION;

  /**
   * The default ECPS phrase. (Mainly for testing purposes)
   */
  @JsonProperty
  public String DEFAULT_ECPS_PHRASE;

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
  public int MAX_SERVICE_RESPONSE_SIZE;

  /**
   * The maximum uncompressed request size in bytes supported on API calls. If uncompressed request size is bigger than
   * MAX_UNCOMPRESSED_REQUEST_SIZE, an error with status code 413 will be sent.
   */
  @JsonProperty
  public long MAX_UNCOMPRESSED_REQUEST_SIZE;

  /**
   * The maximum uncompressed response size in bytes supported on API calls. If uncompressed response size is bigger than
   * MAX_UNCOMPRESSED_RESPONSE_SIZE, an error with status code 513 will be sent.
   */
  @JsonProperty
  public long MAX_UNCOMPRESSED_RESPONSE_SIZE;

  /**
   * The maximum http response size in bytes supported on API calls. If response size is bigger than MAX_HTTP_RESPONSE_SIZE, an error with
   * status code 513 will be sent. Validation is only applied when MAX_HTTP_RESPONSE_SIZE is bigger than zero.
   *
   * @deprecated Use instead MAX_UNCOMPRESSED_RESPONSE_SIZE
   */
  @JsonProperty
  public int MAX_HTTP_RESPONSE_SIZE;

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
   * List of fields, separated by comma, which are optional on feature's namespace property.
   */
  @JsonProperty
  public List<String> FEATURE_NAMESPACE_OPTIONAL_FIELDS = Collections.emptyList();

  @JsonIgnore
  private Map<String, Object> FEATURE_NAMESPACE_OPTIONAL_FIELDS_MAP;

  /**
   * When set, modifies the Stream-Info header name to the value specified.
   */
  @JsonProperty
  public String CUSTOM_STREAM_INFO_HEADER_NAME;

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
   * Flag that indicates whether the creation of features on history spaces contains features in the request payload with uuid.
   */
  @JsonProperty
  public boolean MONITOR_FEATURES_WITH_UUID = true;

  /**
   * Global limit for the maximum amount of versions to keep per space.
   */
  @JsonProperty
  public long MAX_VERSIONS_TO_KEEP = 1_000_000_000;

  /**
   * Flag indicating whether the author should be retrieved from the custom header Author.
   */
  @JsonProperty
  public boolean USE_AUTHOR_FROM_HEADER = false;

  /**
   * Endpoint which includes Maintenance and JOB-API.
   */
  @JsonProperty
  public String HTTP_CONNECTOR_ENDPOINT;

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
  public String DECOMPRESSED_INPUT_SIZE_HEADER_NAME = "X-Decompressed-Input-Size";

  /**
   * The name of the header for reporting the decompressed size of the response payload.
   */
  @JsonProperty
  public String DECOMPRESSED_OUTPUT_SIZE_HEADER_NAME = "X-Decompressed-Output-Size";

  /**
   * ECPS_PHRASE of Default Connector
   */
  @JsonProperty
  public String ECPS_PHRASE;

  /**
   * Max number of parallel running Maintenance Tasks
   */
  @JsonProperty
  public int MAX_CONCURRENT_MAINTENANCE_TASKS;

  /**
   * Defines the time threshold in which a maintenance should be finished. If its reached a warning gets logged.
   */
  @JsonProperty
  public int MISSING_MAINTENANCE_WARNING_IN_HR;

  /**
   * ARN of DynamoDB Table for JOBs
   */
  @JsonProperty
  public String JOBS_DYNAMODB_TABLE_ARN;

  /**
   * S3 Bucket for imports/exports
   */
  @JsonProperty
  public String JOBS_S3_BUCKET;

  /**
   * S3 Bucket for imports/exports
   */
  @JsonProperty
  public String JOBS_S3_BUCKET_REGION = "us-east-1"; // TODO: We should detect the region in which we are!

  /**
   * Set interval for JobQueue processing.
   */
  @JsonProperty
  public int JOB_CHECK_QUEUE_INTERVAL_SECONDS = 60;

  /**
   * Define how many job are allowed to run in parallel
   */
  @JsonProperty
  public int JOB_MAX_RUNNING_JOBS;

  /**
   * List of "connectorId:cloudWatchDBInstanceIdentifier:MaxCapacityUnits"
   */
  @JsonProperty
  protected String JOB_SUPPORTED_RDS;

  private List<String> JOB_SUPPORTED_RDS_cache;

  public @NotNull List<@NotNull String> JOB_SUPPORTED_RDS() {
    if (JOB_SUPPORTED_RDS_cache==null) {
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
   * RDS maximum ACUs
   */
  @JsonProperty
  public int JOB_MAX_RDS_CAPACITY;

  /**
   * RDS maximum CPU Load in percentage
   */
  @JsonProperty
  public int JOB_MAX_RDS_CPU_LOAD;

  /**
   * RDS maximum allowed import bytes
   */
  @JsonProperty
  public long JOB_MAX_RDS_INFLIGHT_IMPORT_BYTES;

  /**
   * RDS maximum allowed idx creations in parallel
   */
  @JsonProperty
  public int JOB_MAX_RDS_MAX_RUNNING_IDX_CREATIONS;

  /**
   * RDS maximum allowed imports in parallel
   */
  @JsonProperty
  public int JOB_MAX_RDS_MAX_RUNNING_IMPORTS;

  /**
   * RDS maximum allowed imports in parallel
   */
  @JsonProperty
  public long JOB_DYNAMO_EXP_IN_DAYS;

  /**
   * Temporary needed for migration phase
   */
  @JsonProperty
  public boolean JOB_OLD_DATABASE_LAYOUT;

  /**
   * Statement Timeout in Seconds
   */
  @JsonProperty
  public int DB_STATEMENT_TIMEOUT_IN_S;

  /**
   * Initial Connection-Pool Size
   */
  @JsonProperty
  public int DB_INITIAL_POOL_SIZE = 5;

  /**
   * Min size of Connection-Pool
   */
  @JsonProperty
  public int DB_MIN_POOL_SIZE = 1;

  /**
   * Max size of Connection-Pool
   */
  @JsonProperty
  public int DB_MAX_POOL_SIZE = 50;

  /**
   * How many connections should get acquired if the pool runs out of available connections.
   */
  @JsonProperty
  public int DB_ACQUIRE_INCREMENT = 1;

  /**
   * How many times will try to acquire a new Connection from the database before giving up.
   */
  @JsonProperty
  public int DB_ACQUIRE_RETRY_ATTEMPTS = 10;

  /**
   * Max Time to wait for a connection checkout - in Seconds
   */
  @JsonProperty
  public int DB_CHECKOUT_TIMEOUT = 10;

  /**
   * Test on checkout if connection is valid
   */
  @JsonProperty
  public boolean DB_TEST_CONNECTION_ON_CHECKOUT;

  /**
   * Hub-Endpoint
   */
  @JsonProperty
  public String HUB_ENDPOINT;
}