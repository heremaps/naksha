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

import static com.here.xyz.util.JsonConfigFile.nullable;

import com.here.xyz.config.ServiceConfig;
import com.here.xyz.connectors.ErrorResponseException;
import com.here.xyz.httpconnector.config.AwsCWClient;
import com.here.xyz.httpconnector.config.JDBCClients;
import com.here.xyz.httpconnector.config.JDBCImporter;
import com.here.xyz.httpconnector.config.JobConfigClient;
import com.here.xyz.httpconnector.config.JobS3Client;
import com.here.xyz.httpconnector.util.scheduler.ImportQueue;
import com.here.xyz.hub.cache.CacheClient;
import com.here.xyz.hub.config.ConnectorConfigClient;
import com.here.xyz.hub.config.SpaceConfigClient;
import com.here.xyz.hub.config.SubscriptionConfigClient;
import com.here.xyz.hub.connectors.BurstAndUpdateThread;
import com.here.xyz.hub.connectors.WarmupRemoteFunctionThread;
import com.here.xyz.hub.rest.admin.MessageBroker;
import com.here.xyz.hub.rest.admin.messages.RelayedMessage;
import com.here.xyz.hub.rest.admin.messages.brokers.Broker;
import com.here.xyz.hub.rest.admin.messages.brokers.NoopBroker;
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
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;

public class Service extends Core {

  private static final Logger logger = LogManager.getLogger();

  private static Service singleton;

  /**
   * Returns the current service instance.
   *
   * @return the current service instance.
   * @throws IllegalStateException if the service not started.
   */
  public static @Nonnull Service get() {
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
  public synchronized static @Nonnull Service start(final @Nullable VertxOptions vertxOptions) throws Exception {
    if (singleton != null) {
      throw new IllegalStateException("The service is already initialized");
    }
    return new Service(vertxOptions);
  }

  public static final String XYZ_HUB_USER_AGENT = "XYZ-Hub/" + BUILD_VERSION;

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
   * A web client to access XYZ Hub nodes and other web resources.
   */
  public final WebClient webClient;

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
  public final JobS3Client jobS3Client;

  /**
   * The client to access job configs
   */
  public final AwsCWClient jobCWClient;

  /**
   * The client to access the database
   */
  public final JDBCImporter jdbcImporter;

  /**
   * Queue for executed Jobs
   */
  public final ImportQueue importQueue;

  public final List<String> supportedConnectors = new ArrayList<>();
  public final HashMap<String, String> rdsLookupDatabaseIdentifier = new HashMap<>();
  public final HashMap<String, Integer> rdsLookupCapacity = new HashMap<>();

  /**
   * The hostname
   */
  private static String hostname;
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

    // Get the message broker synchronously.
    final String brokerName = nullable(ServiceConfig.get().DEFAULT_MESSAGE_BROKER);
    Broker broker = null;
    if (brokerName != null) {
      broker = Broker.valueOf(brokerName);
    }
    if (broker == null) {
      broker = Broker.Redis;
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
    webClient = WebClient.create(vertx,
        new WebClientOptions()
            .setUserAgent(XYZ_HUB_USER_AGENT)
            .setTcpKeepAlive(config.HTTP_CLIENT_TCP_KEEPALIVE)
            .setIdleTimeout(config.HTTP_CLIENT_IDLE_TIMEOUT)
            .setTcpQuickAck(true)
            .setTcpFastOpen(true)
            .setPipelining(config.HTTP_CLIENT_PIPELINING));

    node = new ServiceNode(this, Service.NODE_ID, Service.getHostname(), getPublicPort());
    node.start();
    try {
      for (final String rdsConfig : Service.get().config.JOB_SUPPORTED_RDS()) {
        final String[] config = rdsConfig.split(":");
        final String cId = config[0];
        supportedConnectors.add(cId);
        rdsLookupDatabaseIdentifier.put(cId, config[1]);
        rdsLookupCapacity.put(cId, Integer.parseInt(config[2]));
      }
      supportedConnectors.add(JDBCClients.CONFIG_CLIENT_ID);
    } catch (Exception e) {
      logger.error("Configuration-Error - please check service config!");
      throw new Error("Configuration-Error - please check service config!", e);
    }
    importQueue.commence();

    if (ServiceConfig.get().INSERT_LOCAL_CONNECTORS) {
      connectorConfigClient.insertLocalConnectors();
    }

    // Start threads.
    if (config.START_BURST_AND_UPDATE_THREAD) {
      BurstAndUpdateThread.initialize();
    }
    if (config.START_WARMUP_REMOTE_FUNCTION_THREAD) {
      WarmupRemoteFunctionThread.initialize();
    }

    // Deploy verticles.
    if (config.DEPLOY_XYZ_HUB_REST_VERTICLE) {
      logger.info("Deploy {}", XYZHubRESTVerticle.class.getName());
      final DeploymentOptions options = new DeploymentOptions();
      options.setWorker(false);
      options.setInstances(Runtime.getRuntime().availableProcessors());
      vertx.deployVerticle(XYZHubRESTVerticle.class, options);
    }
    if (config.DEPLOY_PSQL_HTTP_CONNECTOR_VERTICLE) {
      logger.info("Deploy {}", XYZHubRESTVerticle.class.getName());
      final DeploymentOptions options = new DeploymentOptions();
      options.setWorker(false);
      options.setInstances(Runtime.getRuntime().availableProcessors());
      vertx.deployVerticle(XYZHubRESTVerticle.class, options);
    }

    logger.info("XYZ Hub " + BUILD_VERSION + " was started at " + new Date().toString());
    logger.info("Native transport enabled: " + vertx.isNativeTransportEnabled());

    Thread.setDefaultUncaughtExceptionHandler((thread, t) -> logger.error("Uncaught exception: ", t));

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      stopMetricPublishers();
      //This may fail, if we are OOM, but lets at least try.
      logger.warn("XYZ Service is going down at " + new Date().toString());
    }));

    startMetricPublishers();
  }

  /**
   * The service entry point.
   */
  public static void main(String[] arguments) throws Exception {
    String configFilename = null;
    boolean enableDebug = false;
    for (final String arg : arguments) {
      if (arg.startsWith("--config=")) {
        configFilename = arg.substring("--config=".length());
        // Extend a relative path to an absolute path.
        // If no path component found (no slash/backslash), then the config file will be searched in the config directory.
        if (configFilename.indexOf('/') == -1 && configFilename.indexOf('\\') == -1) {
          configFilename = Paths.get(System.getProperty("user.dir"), configFilename).toAbsolutePath().toString();
        }
      } else if (arg.startsWith("--debug")) { // --debug=true will work as well, while --debug
        enableDebug = true;
      }
    }
    // This will ensure the --config parameter taken into account.
    final ServiceConfig config = ServiceConfig.get(ServiceConfig.class, configFilename);
    if (enableDebug) {
      config.DEBUG = true;
      System.out.println("Start service in DEBUG mode ...");
    } else {
      System.out.println("Start service ...");
    }
    start(null);
  }

  public static String getHostname() {
    if (hostname == null) {
      final String hostname = Service.get().config.HOST_NAME;
      if (hostname != null && hostname.length() > 0) {
        Service.hostname = hostname;
      } else {
        try {
          Service.hostname = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
          logger.error("Unable to resolve the hostname using Java's API.", e);
          Service.hostname = "localhost";
        }
      }
    }
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
    if (config.XYZ_HUB_PUBLIC_ENDPOINT == null) {
      return config.HTTP_PORT;
    }
    try {
      URI endpoint = new URI(config.XYZ_HUB_PUBLIC_ENDPOINT);
      int port = endpoint.getPort();
      return port > 0 ? port : 80;
    } catch (URISyntaxException e) {
      return config.HTTP_PORT;
    }
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
}
