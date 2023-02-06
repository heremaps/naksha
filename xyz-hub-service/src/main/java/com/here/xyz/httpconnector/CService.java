/*
 * Copyright (C) 2017-2021 HERE Europe B.V.
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

package com.here.xyz.httpconnector;

import com.here.xyz.httpconnector.config.*;
import com.here.xyz.httpconnector.util.scheduler.ImportQueue;
import com.here.xyz.hub.Core;
import com.here.xyz.hub.Service;
import com.here.xyz.util.JsonConfigFile;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import java.io.IOException;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Vertex deployment of HTTP-Connector.
 */
public class CService extends Core {

  private CService() throws IOException {
    super(null);
  }

  public static final String USER_AGENT = "HTTP-Connector/" + BUILD_VERSION;

  /**
   * The host ID.
   */
  public static final String HOST_ID = UUID.randomUUID().toString();

  /**
   * The client to access databases for maintenanceTasks
   */
  //public static MaintenanceClient maintenanceClient;

  /**
   * The client to access job configs
   */
  public static JobConfigClient jobConfigClient;

  /**
   * The client to access job configs
   */
  public static JobS3Client jobS3Client;

  /**
   * The client to access job configs
   */
  public static AwsCWClient jobCWClient;

  /**
   * The client to access the database
   */
  public static JDBCImporter jdbcImporter;

  /**
   * Queue for executed Jobs
   */
  public static ImportQueue importQueue;

  /**
   * Service Configuration
   */
  public static CServiceConfig configuration;
  /**
   * A web client to access XYZ Hub and other web resources.
   */
  public static WebClient webClient;

  public static final List<String> supportedConnectors = new ArrayList<>();
  public static final HashMap<String, String> rdsLookupDatabaseIdentifier = new HashMap<>();
  public static final HashMap<String, Integer> rdsLookupCapacity = new HashMap<>();

  private static final Logger logger = LogManager.getLogger();

  public static void main(String[] args) {
    VertxOptions vertxOptions = new VertxOptions()
            .setWorkerPoolSize(NumberUtils.toInt(System.getenv(Core.VERTX_WORKER_POOL_SIZE), 128))
            .setPreferNativeTransport(true)
            .setBlockedThreadCheckInterval(TimeUnit.MINUTES.toMillis(15));
//    initialize(vertxOptions, false, "connector-config.json", CService::onConfigLoaded );
  }

  public static void onConfigLoaded(JsonObject jsonConfig) {
    configuration = jsonConfig.mapTo(CServiceConfig.class);

    try {
      for (String rdsConfig : Service.get().config.JOB_SUPPORTED_RDS()) {
        String[] config = rdsConfig.split(":");
        String cId = config[0];
        supportedConnectors.add(cId);
        rdsLookupDatabaseIdentifier.put(cId, config[1]);
        rdsLookupCapacity.put(cId, Integer.parseInt(config[2]));
      }
      supportedConnectors.add(JDBCClients.CONFIG_CLIENT_ID);
    }catch (Exception e){
      logger.error("Configuration-Error - please check service config!");
      throw new RuntimeException("Configuration-Error - please check service config!",e);
    }

    //maintenanceClient = new MaintenanceClient();
    jobConfigClient = JobConfigClient.getInstance();

//    jobConfigClient.init(jobConfigReady -> {
//      if(jobConfigReady.succeeded()) {
//        /** Init webclient */
//        webClient = WebClient.create(vertx, new WebClientOptions()
//                .setUserAgent(USER_AGENT)
//                .setTcpKeepAlive(true)
//                .setIdleTimeout(60)
//                .setTcpQuickAck(true)
//                .setTcpFastOpen(true));
//
//        jdbcImporter = new JDBCImporter();
//        jobS3Client = new JobS3Client();
//        jobCWClient = new AwsCWClient();
//        importQueue = new ImportQueue();
//        /** Start Job-Scheduler */
//        importQueue.commence();
//      }else
//        logger.error("Cant reach jobAPI backend - JOB-API deactivated!");
//    });

    final DeploymentOptions options = new DeploymentOptions()
            .setConfig(jsonConfig)
            .setWorker(false)
            .setInstances(Runtime.getRuntime().availableProcessors() * 2);

//    vertx.deployVerticle(PsqlHttpConnectorVerticle.class, options, result -> {
//      if (result.failed()) {
//        logger.error("Unable to deploy the verticle.");
//        System.exit(1);
//      }
//      logger.info("The http-connector is up and running on port " + configuration.HTTP_PORT );
//    });
  }

  // PsqlHttpConnectorVerticle
  //   -> offers a REST API (internal) that is contacted by XYZ-Hub
  //   -> starts the maintenance too (only on health-checks)
  // CService
  //   -> starts the PsqlHttpConnectorVerticle (Config)
  //   -> starts the job-queue
  //   -> starts the maintenance
  // Service (Config)
  //   -> starts XYZ-Hub API (official REST API)
  //   -> starts the maintenance too (unclear usage)
}