/*
 * Copyright (C) 2017-2024 HERE Europe B.V.
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
package com.here.naksha.lib.hub;

import static com.here.naksha.lib.core.HubInternalIdentifiers.ALL_HUB_INTERNAL_COLLECTIONS;
import static com.here.naksha.lib.core.HubInternalIdentifiers.CONFIGS;
import static com.here.naksha.lib.core.HubInternalIdentifiers.STORAGES;
import static com.here.naksha.lib.core.exceptions.UncheckedException.unchecked;
import static com.here.naksha.lib.hub.NakshaHubAdminStorageIdentifiers.DEFAULT_HUB_ADMIN_MAP_ID;
import static com.here.naksha.lib.hub.NakshaHubAdminStorageIdentifiers.DEFAULT_HUB_ADMIN_STORAGE_ID;
import static naksha.model.Action.CREATED;
import static naksha.model.NakshaContext.currentContext;
import static naksha.model.util.RequestHelper.createFeatureRequest;
import static naksha.model.util.RequestHelper.readFeaturesByIdRequest;
import static naksha.model.util.RequestHelper.readFeaturesByIdsRequest;
import static naksha.model.util.ResultHelper.readFeatureFromResponse;

import com.here.naksha.lib.core.AbstractTask;
import com.here.naksha.lib.core.DefaultRequestLimitManager;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.IRequestLimitManager;
import com.here.naksha.lib.core.exceptions.StorageNotFoundException;
import com.here.naksha.lib.core.models.ExtensionConfig;
import com.here.naksha.lib.core.models.features.Extension;
import com.here.naksha.lib.core.util.IoHelp;
import com.here.naksha.lib.extmanager.ExtensionManager;
import com.here.naksha.lib.extmanager.IExtensionManager;
import com.here.naksha.lib.extmanager.helpers.AmazonS3Helper;
import com.here.naksha.lib.hub.storages.NHAdminStorage;
import com.here.naksha.lib.hub.storages.NHSpaceStorage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import naksha.base.FromJsonOptions;
import naksha.base.JvmBoxingUtil;
import naksha.base.JvmJsonUtil;
import naksha.base.Platform;
import naksha.model.IStorage;
import naksha.model.IWriteSession;
import naksha.model.Naksha;
import naksha.model.NakshaContext;
import naksha.model.NakshaError;
import naksha.model.NakshaVersion;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaStorage;
import naksha.model.objects.NakshaCollection;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaFeatureList;
import naksha.model.objects.NakshaMap;
import naksha.model.request.ErrorResponse;
import naksha.model.request.Request;
import naksha.model.request.Response;
import naksha.model.request.SuccessResponse;
import naksha.model.request.Write;
import naksha.model.request.WriteRequest;
import naksha.model.util.ResultHelper;
import naksha.psql.PgConfig;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NakshaHub implements INaksha {

  /**
   * The id of map used by default
   */
  public static String DEFAULT_HUB_MAP_ID = "naksha-hub";

  /**
   * The id of default NakshaHub Config feature object
   */
  public static final @NotNull String DEF_CFG_ID = "default-config";

  private static final @NotNull Logger logger = LoggerFactory.getLogger(NakshaHub.class);

  /**
   * The NakshaHub config.
   */
  protected final @NotNull NakshaHubConfig nakshaHubConfig;

  /**
   * Singleton instance of physical admin storage implementation
   */
  protected final @NotNull IStorage psqlStorage;
  /**
   * Singleton instance of AdminStorage, which internally uses physical admin storage (i.e. PsqlStorage)
   */
  protected final @NotNull IStorage adminStorageInstance;
  /**
   * Singleton instance of Space Storage, which is responsible to manage admin collections as spaces and support respective read/write
   * operations on spaces
   */
  protected final @NotNull IStorage spaceStorageInstance;

  /**
   * Singleton instance of Extension Manager, which is responsible to manage Naksha extensions cache
   */
  protected @NotNull IExtensionManager extensionManager;

  private final @NotNull String adminMapId;

  @ApiStatus.AvailableSince(NakshaVersion.v2_0_7)
  public NakshaHub(
      final @NotNull String adminPgMasterUrl,
      final @Nullable NakshaHubConfig customCfg,
      final @Nullable String configId) {
      this(
          DEFAULT_HUB_ADMIN_MAP_ID,
          DEFAULT_HUB_ADMIN_STORAGE_ID,
          adminPgMasterUrl,
          customCfg,
          configId
      );
  }

  /*
   * TODO CASL-657:
   *  - support PsqlCluster for more than 1 master instance
   *  - simplify configuraiton flow
   *  - consider switchign from `storageUrl` to `NakshaHubStorageCfg` or something
   */
  @ApiStatus.AvailableSince(NakshaVersion.v2_0_7)
  public NakshaHub(
      final @NotNull String adminMapId,
      final @NotNull String adminStorageId,
      final @NotNull String adminPgMasterUrl,
      final @Nullable NakshaHubConfig customCfg,
      final @Nullable String configId) {
    this.adminMapId = adminMapId;
    // create storage instance upfront
    logger.info("NakshaHub initialization started.");
//    // TODO force create and update?
//    // TODO CASL-657: support clustering
    final NakshaStorage storageConfig = new PgConfig(adminStorageId)
        .withMasterUri(adminPgMasterUrl)
        .withCreate(true)
        .withUpgrade(true);

    //    this.psqlStorage = new PsqlStorage(PsqlStorage.ADMIN_STORAGE_ID, appName, storageUrl);
    logger.info("Initializing Admin storage (if not already).");
    this.psqlStorage = Naksha.useStorage(storageConfig);
    this.adminStorageInstance = new NHAdminStorage(this.psqlStorage);
    this.spaceStorageInstance = new NHSpaceStorage(this, new NakshaEventPipelineFactory(this));
    // setup backend storage DB and Hub config
    NakshaContext nakshaContext = setupMapAndContext(adminMapId);
    final NakshaHubConfig finalCfg = this.storageSetup(customCfg, configId, nakshaContext);
    if (finalCfg == null) {
      throw new RuntimeException("Server configuration not found! Neither in Admin storage nor a default file.");
    }
    this.nakshaHubConfig = finalCfg;
    if (this.nakshaHubConfig.getExtensionConfigParams() != null) {
      this.extensionManager = ExtensionManager.getInstance(this);
    } else {
      logger.warn("ExtensionManager is not initialised due to extensionConfigParams not found.");
    }
    // Setting Concurrency Thresholds
    logger.info("Value of maxParallelRequestsPerCPU is {}", nakshaHubConfig.getMaxParallelRequestsPerCPU());
    logger.info("Value of maxPctParallelRequestsPerActor is {}", nakshaHubConfig.getMaxPctParallelRequestsPerActor());
    IRequestLimitManager requestLimitManager = new DefaultRequestLimitManager(
        nakshaHubConfig.getMaxParallelRequestsPerCPU(), nakshaHubConfig.getMaxPctParallelRequestsPerActor());
    logger.info("Instance level limit is {}", requestLimitManager.getInstanceLevelLimit());
    AbstractTask.setConcurrencyLimitManager(requestLimitManager);

    logger.info("NakshaHub initialization done!");
  }

  @Override
  public @NotNull String getAdminMapId() {
    return adminMapId;
  }

  private NakshaContext setupMapAndContext(String mapId) {
    NakshaMap map = new NakshaMap().withId(mapId);
    Write createMap = new Write().upsertMap(map, false);
    NakshaContext initialContext = NakshaContext.currentContext().withAuthor(NakshaHubConfig.defaultAppName());
    psqlStorage.runInWriteSession(SessionOptions.from(initialContext), writer -> {
      Response response = writer.execute(new WriteRequest().add(createMap));
      if(!(response instanceof SuccessResponse)) {
        writer.rollback();
        throw new RuntimeException("Map creation for Naksha Hub Admin failed: " + response);
      }
      writer.commit();
    });
    NakshaContext nakshaHubAdminContext = NakshaContext.newInstance(NakshaHubConfig.defaultAppName(), NakshaHubConfig.defaultAppName());
    nakshaHubAdminContext.setMapId(mapId);
    nakshaHubAdminContext.attachToCurrentThread();
    return nakshaHubAdminContext;
  }

  private @Nullable NakshaHubConfig storageSetup(
      final @Nullable NakshaHubConfig customCfg,
      final @Nullable String configId,
      final @NotNull NakshaContext nakshaContext
  ) {
    // 1. Create all Admin collections in Admin DB
    createAdminCollections(nakshaContext);

    // 2. fetch / add latest config
    return configSetup(nakshaContext, customCfg, configId);
  }

  private void createAdminCollections(NakshaContext nakshaContext) {
    getAdminStorage().runInWriteSession(SessionOptions.from(nakshaContext, true), admin -> {
      logger.info("WriteCollections Request for {}, against Admin storage.", ALL_HUB_INTERNAL_COLLECTIONS);
      final Response createAdminCollectionsResponse = admin.execute(upsertAdminCollectionsRequest());
      if (createAdminCollectionsResponse instanceof SuccessResponse successResponse) {
        NakshaFeatureList createdCollections = successResponse.getFeatures();
        for (NakshaFeature createdCollection : createdCollections) {
          if (Objects.equals(
              CREATED.getValue(),
              createdCollection.getProperties().getXyz().getAction())) {
            logger.info("Collection {} successfully created.", createdCollection.getId());
          }
        }
        admin.commit();
      } else {
        if (createAdminCollectionsResponse instanceof ErrorResponse errorResponse) {
          NakshaError err = errorResponse.getError();
          logger.error("Could not create admin collections (error code: {})", err.getCode(), err.getCause());
        } else {
          logger.error("Unknown response type: {}", createAdminCollectionsResponse.getClass());
        }
        admin.rollback();
      }
    });
  }

  private static WriteRequest upsertAdminCollectionsRequest() {
    final WriteRequest writeRequest = new WriteRequest();
    for (String adminCollectionId : ALL_HUB_INTERNAL_COLLECTIONS) {
      writeRequest.add(new Write().upsertCollection(new NakshaCollection(adminCollectionId)));
    }
    return writeRequest;
  }

  private @Nullable NakshaHubConfig configSetup(
      final @NotNull NakshaContext nakshaContext,
      final @Nullable NakshaHubConfig customCfg,
      final @Nullable String configId) {
    /*
     * Config preference, for a given configId (e.g. "custom-config"):
     * 1. Custom config - If provided, persist the same in DB, and use the same for NakshaHub
     * 2. DB custom config - If Database already has custom config (e.g. "custom-config"), use the same
     * 3. DB default config - If Database has default config - "default-config", use the same
     * 3. Default config - Fallback to default config from file - "default-config"
     */
    logger.info("Running config setup for Nakshs Hub against Admin storage.");
    return getAdminStorage().useWriteSession(SessionOptions.from(nakshaContext, true), admin -> {
      if (customCfg != null) {
        WriteRequest writeCustomCfg = new WriteRequest()
            .add(new Write().upsertFeature(NakshaContext.mapId(), CONFIGS, customCfg));
        Response writeCustomCfgResponse = admin.execute(writeCustomCfg);
        if (writeCustomCfgResponse instanceof SuccessResponse) {
          admin.commit();
          return customCfg;
        } else {
          admin.rollback();
          if (writeCustomCfgResponse instanceof ErrorResponse errorResponse) {
            NakshaError error = errorResponse.getError();
            throw unchecked(new Exception(
                "Unable to add custom config in Admin DB (error code: " + error.getCode() + " )",
                error.getCause()));
          }
          throw unchecked(new Exception("Unable to add custom config in Admin DB (unexpected response: "
                                        + writeCustomCfgResponse + ")"));
        }
      }

      // load custom + default config from DB (if available)
      final NakshaHubConfig configFoundInDb = fetchHubConfigFromDb(configId, admin);
      if (configFoundInDb != null) {
        return configFoundInDb;
      } else {
        logger.info("No custom/default config found in Admin DB.");
      }

      // load default config from file (as DB didn't have custom/default config)
      NakshaHubConfig defCfgFromFile = readHubDefaultConfigFromFile();

      // Persist default config in Admin DB
      logger.info("Persisting default NakshaHub config from file...");
      writeConfig(admin, defCfgFromFile);
      return defCfgFromFile;
    });
  }

  /**
   * Write config to Naksha Hub - if it already exists or there's a conflict - there's no failure and the previously stored version will be
   * retained.
   *
   * @param admin  write session to admin space
   * @param config config to be stored
   */
  private void writeConfig(IWriteSession admin, NakshaHubConfig config) {
    final Request writeDefCfg = createFeatureRequest(CONFIGS, config);
    final Response writeConfigResp = admin.execute(writeDefCfg);
    if (writeConfigResp instanceof SuccessResponse) {
      admin.commit();
    } else {
      admin.rollback();
      if (writeConfigResp instanceof ErrorResponse errorResponse) {
        NakshaError error = errorResponse.getError();
        if (NakshaError.CONFLICT.equals(error.getCode())) {
          logger.info("Ignoring CONFLICT encountered when writing NakshaHubConfig");
          return;
        }
        throw unchecked(new Exception(
            "Unable to add default config in Admin DB (error code:  " + error.getCode() + ")",
            error.getCause()));
      }
      throw unchecked(new Exception(
          "Unable to add default config in Admin DB (unknown response: " + writeConfigResp + ")"));
    }
  }

  private NakshaHubConfig readHubDefaultConfigFromFile() {
    final String configJson = IoHelp.readResource("config/" + DEF_CFG_ID + ".json");
    NakshaHubConfig defCfg = JvmJsonUtil.readJsonAs(configJson, NakshaHubConfig.class);
    defCfg.setId(DEF_CFG_ID); // overwrite Id to desired value
    return defCfg;
  }

  private NakshaHubConfig fetchHubConfigFromDb(String configId, IWriteSession admin) {
    final List<String> cfgIdList = (configId != null) ? List.of(configId, DEF_CFG_ID) : List.of(DEF_CFG_ID);
    final Request readAdminConfigs = readFeaturesByIdsRequest(CONFIGS, cfgIdList);
    final Response readAdminConfigsResp = admin.execute(readAdminConfigs);
    if (readAdminConfigsResp instanceof SuccessResponse successResponse) {
      List<NakshaHubConfig> nakshaHubConfigs =
          ResultHelper.extractResponseItems(successResponse, NakshaHubConfig.class);
      NakshaHubConfig defDbCfg = null;
      for (final NakshaHubConfig cfg : nakshaHubConfigs) {
        if (cfg.getId().equals(configId)) {
          return cfg; // return custom config - it has priority
        }
        if (cfg.getId().equals(DEF_CFG_ID)) {
          defDbCfg = cfg;
        }
      }
      return defDbCfg; // return default config from DB - if no custom found, can be null if also missing
    } else {
      admin.rollback();
      if (readAdminConfigsResp instanceof ErrorResponse errorResponse) {
        NakshaError error = errorResponse.getError();
        throw unchecked(new Exception(
            "Unable to read custom/default config from Admin DB (error code: " + error.getCode() + ")",
            error.getCause()));
      }
      throw unchecked(new Exception("Unable to read custom/default config from Admin DB (unexpected response: "
                                    + readAdminConfigsResp + ")"));
    }
  }

  @Override
  @ApiStatus.AvailableSince(NakshaVersion.v2_0_7)
  public @NotNull <T extends NakshaFeature> T getConfig() {
    return (T) this.nakshaHubConfig;
  }

  @Override
  public @NotNull ExtensionConfig getExtensionConfig() {
    final ExtensionConfigParams extensionConfigParams = nakshaHubConfig.getExtensionConfigParams();
    if (!extensionConfigParams.getExtensionRootPath().startsWith("s3://")) {
      throw new UnsupportedOperationException(
          "ExtensionRootPath must be a valid s3 bucket url which should be prefixed with s3://");
    }

    List<Extension> extList = loadExtensionConfigFromS3(extensionConfigParams.getExtensionRootPath());
    return new ExtensionConfig(
        System.currentTimeMillis() + extensionConfigParams.getIntervalMs(),
        extList,
        extensionConfigParams.getWhiteListClasses(),
        this.nakshaHubConfig.getEnv().toLowerCase());
  }

  private List<Extension> loadExtensionConfigFromS3(String extensionRootPath) {
    AmazonS3Helper s3Helper = new AmazonS3Helper();
    final String bucketName = s3Helper.getS3Uri(extensionRootPath).bucket().get();

    List<String> list = s3Helper.listKeysInBucket(extensionRootPath);
    List<Extension> extList = new ArrayList<>();
    list.stream().forEach(extensionPath -> {
      String filePath =
          "s3://" + bucketName + "/" + extensionPath + "latest-" + nakshaHubConfig.getEnv().toLowerCase() + ".txt";
      String version;
      try {
        version = s3Helper.getFileContent(filePath);
      } catch (Exception e) {
        logger.error("Failed to read extension content from {}", filePath, e);
        return;
      }

      String bits[] = extensionPath.split("/");
      String extensionId = bits[bits.length - 1];

      filePath = "s3://" + bucketName + "/" + extensionPath + extensionId + "-" + version + "."
                 + nakshaHubConfig.getEnv().toLowerCase().toLowerCase() + ".json";
      String exJson;
      try {
        exJson = s3Helper.getFileContent(filePath);
      } catch (Exception e) {
        logger.error("Failed to read extension meta data from {} ", filePath, e);
        return;
      }
      Extension extension;
      try {
        extension = JvmBoxingUtil.box(Platform.fromJSON(exJson, FromJsonOptions.DEFAULT), Extension.class);
        extList.add(extension);
      } catch (Exception e) {
        logger.error("Failed to convert extension meta data to Extension object. {} ", exJson, e);
        return;
      }
    });
    return extList;
  }

  @Override
  public @NotNull ClassLoader getClassLoader(@NotNull String extensionId) {
    return this.extensionManager.getClassLoader(extensionId);
  }

  @Override
  @ApiStatus.AvailableSince(NakshaVersion.v2_0_7)
  public @NotNull IStorage getAdminStorage() {
    return this.adminStorageInstance;
  }

  @Override
  @ApiStatus.AvailableSince(NakshaVersion.v2_0_7)
  public @NotNull IStorage getSpaceStorage() {
    return this.spaceStorageInstance;
  }

  @Override
  @ApiStatus.AvailableSince(NakshaVersion.v2_0_7)
  public @NotNull IStorage getStorageById(final @NotNull String storageId) {
    return getAdminStorage().useReadSession(SessionOptions.from(currentContext()), admin -> {
      Request readStorageById = readFeaturesByIdRequest(STORAGES, storageId);
      Response readStorageByIdResp = admin.execute(readStorageById);
      if (readStorageByIdResp instanceof SuccessResponse successResponse) {
        NakshaStorage storageConfig = readFeatureFromResponse(successResponse, NakshaStorage.class);
        if (storageConfig == null) {
          throw unchecked(new StorageNotFoundException(storageId));
        }
        return Naksha.useStorage(storageConfig);
      } else {
        if (readStorageByIdResp instanceof ErrorResponse errorResponse) {
          NakshaError error = errorResponse.getError();
          throw unchecked(new Exception(
              "Could not fetch storage config for id '" + storageId + "' (error code: "
              + error.getCode() + ")",
              error.getCause()));
        }
        throw unchecked(new Exception("Exception fetching storage config for id '" + storageId
                                      + "' (unknown response: " + readStorageByIdResp + " )"));
      }
    });
  }
}
