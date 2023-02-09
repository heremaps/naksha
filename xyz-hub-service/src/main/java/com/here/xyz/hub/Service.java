/*
 * Copyright (C) 2017-2022 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package com.here.xyz.hub;

import static com.here.xyz.hub.AbstractHttpServerVerticle.STREAM_INFO_CTX_KEY;
import static com.here.xyz.util.JsonConfigFile.nullable;

import com.here.xyz.config.XyzConfig;
import com.here.xyz.httpconnector.PsqlHttpConnectorVerticle;
import com.here.xyz.httpconnector.config.AwsCWClient;
import com.here.xyz.httpconnector.config.JDBCImporter;
import com.here.xyz.httpconnector.config.JobConfigClient;
import com.here.xyz.httpconnector.config.JobS3Client;
import com.here.xyz.httpconnector.util.scheduler.ImportQueue;
import com.here.xyz.hub.auth.Authorization.AuthorizationType;
import com.here.xyz.hub.cache.CacheClient;
import com.here.xyz.hub.config.ConnectorConfigClient;
import com.here.xyz.hub.config.SpaceConfigClient;
import com.here.xyz.hub.config.SubscriptionConfigClient;
import com.here.xyz.hub.connectors.BurstAndUpdateThread;
import com.here.xyz.hub.connectors.WarmupRemoteFunctionThread;
import com.here.xyz.hub.rest.admin.MessageBroker;
import com.here.xyz.hub.rest.admin.messages.RelayedMessage;
import com.here.xyz.hub.rest.admin.messages.brokers.Broker;
import com.here.xyz.hub.util.metrics.GcDurationMetric;
import com.here.xyz.hub.util.metrics.GlobalInflightRequestMemory;
import com.here.xyz.hub.util.metrics.GlobalUsedRfcConnections;
import com.here.xyz.hub.util.metrics.MajorGcCountMetric;
import com.here.xyz.hub.util.metrics.MemoryMetric;
import com.here.xyz.hub.util.metrics.base.CWBareValueMetricPublisher;
import com.here.xyz.hub.util.metrics.base.MetricPublisher;
import com.here.xyz.hub.util.metrics.net.ConnectionMetrics;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.RoutingContext;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Service extends Core {

  private static final Logger logger = LogManager.getLogger();

  private static Service singleton;

  /**
   * Returns the current service instance.
   *
   * @return the current service instance.
   * @throws IllegalStateException if the service not started.
   */
  public static @NotNull Service get() {
    if (singleton == null) {
      throw new IllegalStateException("Service not started");
    }
    return singleton;
  }

  /**
   * Start the service.
   *
   * @param vertxOptions the (optional) vertx options.
   * @return the booting service.
   * @throws Exception if any error occurred.
   */
  public synchronized static @NotNull Service start(final @Nullable VertxOptions vertxOptions) throws Exception {
    if (singleton != null) {
      throw new IllegalStateException("The service is already initialized");
    }
    return new Service(vertxOptions);
  }

  /**
   * The host ID.
   */
  public static final String NODE_ID = UUID.randomUUID().toString();

  /**
   * The client to access the space configuration.
   */
  public final SpaceConfigClient spaceConfigClient;

  /**
   * The client to access the connector configuration.
   */
  public final ConnectorConfigClient connectorConfigClient;

  /**
   * The client to access the subscription configuration.
   */
  public final SubscriptionConfigClient subscriptionConfigClient;

  /**
   * The cache client for the service.
   */
  public final CacheClient cacheClient;

  /**
   * The node's MessageBroker which is used to send AdminMessages.
   */
  public final MessageBroker messageBroker;

  /**
   * The client to access job configs
   */
  public final JobConfigClient jobConfigClient;

  /**
   * The node of this service.
   */
  public final ServiceNode node;

  /**
   * The client to access job configs
   */
  public final @NotNull JobS3Client jobS3Client;

  /**
   * The client to access job configs
   */
  public final @NotNull AwsCWClient jobCWClient;

  /**
   * The client to access the database
   */
  public final @NotNull JDBCImporter jdbcImporter;

  /**
   * Queue for executed Jobs
   */
  public final @NotNull ImportQueue importQueue;

  // TODO: For what do we need this?
  public final List<String> supportedConnectors = new ArrayList<>();
  public final HashMap<String, String> rdsLookupDatabaseIdentifier = new HashMap<>();
  public final HashMap<String, Integer> rdsLookupCapacity = new HashMap<>();

  /**
   * Returns the authorization type; if no valid value is explicitly defined, {@link AuthorizationType#DUMMY DUMMY} is returned.
   *
   * @return the authorization type.
   */
  public @NotNull AuthorizationType config_XYZ_HUB_AUTH() {
    try {
      return AuthorizationType.valueOf(config.XYZ_HUB_AUTH);
    } catch (Exception e) {
      return AuthorizationType.DUMMY;
    }
  }

  /**
   * The HTTP server of the XYZ-Hub, if the XYZ-Hub verticle is deployed.
   */
  public final @NotNull HttpServer httpSever;

  /**
   * The hostname.
   */
  private String hostname;
  public static final boolean IS_USING_ZGC = isUsingZgc();

  private static final List<MetricPublisher<?>> metricPublishers = new LinkedList<>();

  /**
   * Only called internally from {@link #start(VertxOptions)}, being synchronized, so the constructor is as well synchronized.
   *
   * @param vertxOptions optional pre-configured vertx options.
   * @throws Exception if any error occurred.
   */
  private Service(final @Nullable VertxOptions vertxOptions) throws Exception {
    super(vertxOptions);
    singleton = this;

    if (nullable(config.STORAGE_DB_URL) == null) {
      throw new Error("Missing 'STORAGE_DB_URL' in configuration");
    }
    if (nullable(config.STORAGE_DB_USER) == null) {
      throw new Error("Missing 'STORAGE_DB_USER' in configuration");
    }
    if (nullable(config.STORAGE_DB_PASSWORD) == null) {
      throw new Error("Missing 'STORAGE_DB_PASSWORD' in configuration");
    }

    // Get the message broker synchronously.
    final String brokerName = nullable(XyzConfig.get().DEFAULT_MESSAGE_BROKER);
    Broker broker = null;
    if (brokerName != null) {
      broker = Broker.valueOf(brokerName);
    }
    if (broker == null) {
      broker = Broker.Noop;
    }
    messageBroker = broker.instance.get();
    // End of gathering of message broker.

    cacheClient = CacheClient.getInstance();
    spaceConfigClient = SpaceConfigClient.getInstance();
    connectorConfigClient = ConnectorConfigClient.getInstance();
    subscriptionConfigClient = SubscriptionConfigClient.getInstance();
    jobConfigClient = JobConfigClient.getInstance();
    jdbcImporter = new JDBCImporter();
    jobS3Client = new JobS3Client();
    jobCWClient = new AwsCWClient();
    importQueue = new ImportQueue();

    node = new ServiceNode(this, Service.NODE_ID, getHostname(), getPublicPort());
    node.start();
// TODO: What does this do, do we need it?
//    try {
//      for (final String rdsConfig : Service.get().coreConfig.JOB_SUPPORTED_RDS()) {
//        final String[] config = rdsConfig.split(":");
//        final String cId = config[0];
//        supportedConnectors.add(cId);
//        rdsLookupDatabaseIdentifier.put(cId, config[1]);
//        rdsLookupCapacity.put(cId, Integer.parseInt(config[2]));
//      }
//      supportedConnectors.add(JDBCClients.CONFIG_CLIENT_ID);
//    } catch (Exception e) {
//      logger.error("Configuration-Error - please check service config!");
//      throw new Error("Configuration-Error - please check service config!", e);
//    }
    importQueue.commence();

    // Start the HTTP server.
    logger.info("Listen at port {}", config.HTTP_PORT);
    httpSever = vertx
        .createHttpServer(SERVER_OPTIONS())
        .requestHandler(router)
        .listen(config.HTTP_PORT)
        .result();

    // Deploy verticles.
    if (config.DEPLOY_XYZ_HUB_REST_VERTICLE) {
      logger.info("Deploy {}", XYZHubRESTVerticle.class.getName());
      final DeploymentOptions options = new DeploymentOptions();
      options.setWorker(false);
      options.setInstances(Runtime.getRuntime().availableProcessors());
      vertx.deployVerticle(XYZHubRESTVerticle.class, options);

      if (config.INSERT_LOCAL_CONNECTORS) {
        connectorConfigClient.insertLocalConnectors();
      }
      if (config.START_BURST_AND_UPDATE_THREAD) {
        BurstAndUpdateThread.initialize();
      }
      if (config.START_WARMUP_REMOTE_FUNCTION_THREAD) {
        WarmupRemoteFunctionThread.initialize();
      }
    }

    if (config.DEPLOY_PSQL_HTTP_CONNECTOR_VERTICLE) {
      logger.info("Deploy {}", PsqlHttpConnectorVerticle.class.getName());
      final DeploymentOptions options = new DeploymentOptions();
      options.setWorker(false);
      options.setInstances(Runtime.getRuntime().availableProcessors());
      vertx.deployVerticle(PsqlHttpConnectorVerticle.class, options);
    }

    logger.info("XYZ Hub {} was started at {}", BUILD_VERSION, new Date());
    logger.info("Native transport enabled: {}", vertx.isNativeTransportEnabled());

    Thread.setDefaultUncaughtExceptionHandler(logger::error);
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      if (config.START_METRICS) {
        stopMetricPublishers();
      }
      //This may fail, if we are OOM, but lets at least try.
      logger.info("XYZ Service is going down at {}", new Date());
    }));

    if (config.START_METRICS) {
      startMetricPublishers();
    }
  }

  protected @NotNull HttpServerOptions SERVER_OPTIONS() {
    return new HttpServerOptions()
        .setCompressionSupported(true)
        .setDecompressionSupported(true)
        .setHandle100ContinueAutomatically(true)
        .setTcpQuickAck(true)
        .setTcpFastOpen(true)
        .setMaxInitialLineLength(16 * 1024)
        .setIdleTimeout(300);
  }

  /**
   * The service entry point.
   */
  public static void main(String[] arguments) throws Exception {
    boolean enableDebug = false;
    for (final String arg : arguments) {
      if (arg.startsWith("--debug")) { // --debug=true will work as well, while --debug
        enableDebug = true;
      }
    }
    // This will ensure the --config parameter taken into account.
    if (enableDebug) {
      final XyzConfig config = XyzConfig.get();
      config.DEBUG = true;
      logger.info("Start service in DEBUG mode ...");
    } else {
      logger.info("Start service ...");
    }
    start(null);
  }

  public @NotNull String getHostname() {
    if (hostname == null) {
      String hostname = nullable(Service.get().config.HTTP_HOST);
      if (hostname == null) {
        try {
          hostname = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
          logger.error("Unable to resolve the hostname using Java's API.", e);
          hostname = "localhost";
        }
      }
      this.hostname = hostname;
    }
    assert hostname != null;
    return hostname;
  }

  public static long getUsedMemoryBytes() {
    return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
  }

  public static float getUsedMemoryPercent() {
    float used = getUsedMemoryBytes();
    float total = Runtime.getRuntime().totalMemory();
    return used / total * 100;
  }

  private void startMetricPublishers() {
    if (config.PUBLISH_METRICS) {
      ConnectionMetrics.initialize();
      metricPublishers.add(new CWBareValueMetricPublisher(new MemoryMetric("JvmMemoryUtilization")));
      metricPublishers.add(new CWBareValueMetricPublisher(new MajorGcCountMetric("MajorGcCount")));
      metricPublishers.add(new CWBareValueMetricPublisher(new GcDurationMetric("GcDuration")));
      metricPublishers.add(new CWBareValueMetricPublisher(new GlobalUsedRfcConnections("GlobalUsedRfcConnections")));
      metricPublishers.add(new CWBareValueMetricPublisher(new GlobalInflightRequestMemory("GlobalInflightRequestMemory")));
      metricPublishers.addAll(ConnectionMetrics.startConnectionMetricPublishers());
    }
  }

  private static boolean isUsingZgc() {
    List<GarbageCollectorMXBean> gcMxBeans = ManagementFactory.getGarbageCollectorMXBeans();
    for (GarbageCollectorMXBean gcMxBean : gcMxBeans) {
      if (gcMxBean.getName().startsWith("ZGC")) {
        logger.info("Service is using ZGC.");
        return true;
      }
    }
    return false;
  }

  private void stopMetricPublishers() {
    metricPublishers.forEach(MetricPublisher::stop);
  }

  public int getPublicPort() {
      return config.PUBLIC_HTTP_PORT;
//    try {
//      URI endpoint = new URI(coreConfig.XYZ_HUB_PUBLIC_ENDPOINT);
//      int port = endpoint.getPort();
//      return port > 0 ? port : 80;
//    } catch (URISyntaxException e) {
//      return coreConfig.HTTP_PORT;
//    }
  }

  /**
   * @return The "environment ID" of this service deployment.
   */
  public String getEnvironmentIdentifier() {
    if (config.ENVIRONMENT_NAME == null) {
      return "default";
    }
    if (config.AWS_REGION != null) {
      return config.ENVIRONMENT_NAME + "_" + config.AWS_REGION;
    }
    return config.ENVIRONMENT_NAME;
  }

  /**
   * That message can be used to change the log-level of one or more service-nodes. The specified level must be a valid log-level. As this
   * is a {@link RelayedMessage} it can be sent to a specific service-node or to all service-nodes regardless of the first service node by
   * which it was received.
   * <p>
   * Specifying the property {@link RelayedMessage#relay} to true will relay the message to the specified destination. If no destination is
   * specified the message will be relayed to all service-nodes (broadcast).
   */
  @SuppressWarnings("unused")
  static class ChangeLogLevelMessage extends RelayedMessage {

    private String level;

    public String getLevel() {
      return level;
    }

    public void setLevel(String level) {
      this.level = level;
    }

    @Override
    protected void handleAtDestination() {
      logger.info("LOG LEVEL UPDATE requested. New level will be: " + level);

      Configurator.setAllLevels(LogManager.getRootLogger().getName(), Level.getLevel(level));

      logger.info("LOG LEVEL UPDATE performed. New level is now: " + level);
    }
  }

  public static void addStreamInfo(final RoutingContext context, String key, Object value) {
    final Object raw = context.get(STREAM_INFO_CTX_KEY);
    final Map<String, Object> map;
    if (raw instanceof Map) {
      //noinspection unchecked
      map = (Map<String, Object>) raw;
    } else {
      map = new HashMap<>();
      context.put(STREAM_INFO_CTX_KEY, map);
    }
    map.put(key, value);
  }
}
