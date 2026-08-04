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

import static com.here.naksha.lib.core.HubInternalIdentifiers.*;
import static com.here.naksha.lib.core.exceptions.UncheckedException.unchecked;
import static naksha.base.Action.CREATE;
import static naksha.base.NakshaExceptionKt.illegalArg;
import static naksha.base.NakshaExceptionKt.illegalState;
import static naksha.base.NakshaExceptionKt.internalError;
import static naksha.base.Base.proxy;
import static naksha.model.NakshaContext.currentContext;
import static naksha.model.util.RequestHelper.createFeatureRequest;
import static naksha.model.util.RequestHelper.readFeaturesByIdRequest;
import static naksha.model.util.RequestHelper.readFeaturesByIdsRequest;
import static naksha.model.util.ResultHelper.extractResponseItems;
import static naksha.model.util.ResultHelper.readFeatureFromResponse;

import com.here.naksha.lib.core.AbstractTask;
import com.here.naksha.lib.core.DefaultRequestLimitManager;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.IRequestLimitManager;
import com.here.naksha.lib.core.exceptions.StorageNotFoundException;
import com.here.naksha.lib.core.models.ExtensionConfig;
import com.here.naksha.lib.core.models.features.Extension;
import com.here.naksha.lib.core.models.naksha.EventHandlerConfig;
import com.here.naksha.lib.core.util.IoHelp;
import com.here.naksha.lib.extmanager.ExtensionManager;
import com.here.naksha.lib.extmanager.FileClient;
import com.here.naksha.lib.extmanager.IExtensionManager;
import com.here.naksha.lib.extmanager.helpers.FileClientFactory;
import com.here.naksha.lib.hub.storages.NHAdminStorage;
import com.here.naksha.lib.hub.storages.NHSpaceStorage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import naksha.base.FromJsonOptions;
import naksha.base.JvmBoxingUtil;
import naksha.base.JvmJsonUtil;
import naksha.base.Base;
import naksha.model.IStorage;
import naksha.model.IWriteSession;
import naksha.model.Naksha;
import naksha.model.NakshaContext;
import naksha.base.NakshaError;
import naksha.model.NakshaVersion;
import naksha.model.SessionOptions;
import naksha.model.objects.*;
import naksha.model.request.ErrorResponse;
import naksha.model.request.ReadFeatures;
import naksha.model.request.Request;
import naksha.model.request.Response;
import naksha.model.request.SuccessResponse;
import naksha.model.request.Write;
import naksha.model.request.WriteRequest;
import naksha.model.request.query.AnyOp;
import naksha.model.request.query.IPropertyQuery;
import naksha.model.request.query.PAnd;
import naksha.model.request.query.PQuery;
import naksha.model.request.query.Property;
import naksha.model.util.ResultHelper;
import naksha.psql.PgConfig;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NakshaHub implements INaksha {

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
   * Singleton instance of physical admin storage implementation.
   */
  protected final @NotNull IStorage psqlStorage;
  /**
   * Singleton instance of AdminStorage, which internally uses physical admin storage (i.e. PsqlStorage).
   */
  protected final @NotNull IStorage adminStorageInstance;
  /**
   * Singleton instance of Space Storage, which is responsible to manage admin collections as spaces and support respective read/write
   * operations on spaces. This storage is a wrapper on top of the {@link #adminStorageInstance} adding additional security constraints.
   */
  protected final @NotNull IStorage spaceStorageInstance;

  /**
   * Singleton instance of Extension Manager, which is responsible to manage Naksha extensions cache.
   */
  protected @NotNull IExtensionManager extensionManager;

  private final @NotNull String adminMapId;

  /**
   * The extensionId property path in handler JSON.
   */
  protected static final String[] EXTN_ID_PROP_PATH = {"extensionId"};

  @ApiStatus.AvailableSince(NakshaVersion.v2_0_7)
  public NakshaHub(
      final @NotNull String adminPgMasterUrl,
      final @Nullable NakshaHubConfig customCfg,
      final @Nullable String configId) {
      this(
          NakshaHubAdminStorageIdentifiers.getHubAdminMapId(),
          NakshaHubAdminStorageIdentifiers.getHubAdminStorageId(),
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
    final var nakshaContext = setupMapAndContext(adminMapId);
    final var finalCfg = this.storageSetup(customCfg, configId, nakshaContext);
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

  @Override
  public @NotNull NakshaDatabase getAdminDatabase() {
    final var adminDb = adminDatabase;
    if (adminDb == null) throw illegalState("The admin storage has not been initialized");
    return adminDb;
  }

  @Override
  public @NotNull NakshaCollection getAdminCollection(@NotNull String collectionId) {
    if (adminCollections.isEmpty()) throw illegalState("The admin storage has not been initialized");
    final var collection = adminCollections.get(collectionId);
    if (collection == null) throw illegalArg("Unknown admin collection: "+collectionId);
    return collection;
  }

  @Override
  public @NotNull NakshaCatalog getAdminCatalog() {
    final var adminCatalog = this.adminCatalog;
    if (adminCatalog == null) throw illegalState("The admin storage has not been initialized");
    return adminCatalog;
  }

  /** The admin database, created by {@link #setupMapAndContext(String)}. */
  private NakshaDatabase adminDatabase;
  /** The admin catalog within the {@link #adminDatabase}, created by {@link #setupMapAndContext(String)}. */
  private NakshaCatalog adminCatalog;
  /** The admin collections within the {@link #adminCatalog}, created by {@link #createAdminCollections(NakshaContext)}. */
  private final ConcurrentHashMap<String, NakshaCollection> adminCollections = new ConcurrentHashMap<>();

  /**
   * Does create the {@link #adminDatabase} and {@link #adminCatalog}.
   * @param mapId the identifier of the admin-catalog.
   * @return the context.
   */
  private @NotNull NakshaContext setupMapAndContext(@NotNull String mapId) {
    final NakshaContext initialContext = NakshaContext.currentContext().withAuthor(NakshaHubConfig.defaultAppName());
    try (final var writer = psqlStorage.newWriteSession(SessionOptions.from(initialContext))) {
      this.adminDatabase = new NakshaDatabase(writer);
      final var adminCatalog = new NakshaCatalog(mapId, adminDatabase);
      final var createMap = new Write().upsertCatalog(adminCatalog, false);
      final var response = writer.execute(new WriteRequest().add(createMap));
      if (response instanceof SuccessResponse successResponse) {
        final var features = successResponse.getFeatures();
        if (features.size() != 1) throw internalError("Expected only one feature as response to Naksha-Hub admin catalog creation, but got: "+features.size());
        writer.commit();
        this.adminCatalog = proxy(features.getFirst(), NakshaCatalog.class);
      } else {
        writer.rollback();
        throw internalError("Failed to create admin catalog for Naksha-Hub: " + response);
      }
    }
    final var nakshaHubAdminContext = NakshaContext.newInstance(NakshaHubConfig.defaultAppName(), NakshaHubConfig.defaultAppName());
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
    return configSetup(nakshaContext, customCfg, configId, adminMapId);
  }

  private void createAdminCollections(@NotNull NakshaContext nakshaContext) {
    getAdminStorage().runInWriteSession(SessionOptions.from(nakshaContext, true), admin -> {
      logger.info("WriteCollections Request for {}, against Admin storage.", ALL_HUB_INTERNAL_COLLECTIONS);
      final Response createAdminCollectionsResponse = admin.execute(upsertAdminCollectionsRequest());
      if (createAdminCollectionsResponse instanceof SuccessResponse successResponse) {
        NakshaFeatureList createdCollections = successResponse.getFeatures();
        for (NakshaFeature createdCollection : createdCollections) {
          if (Objects.equals(CREATE.getValue(), createdCollection.getProperties().getXyz().getAction())) {
            logger.info("Collection {} successfully created.", createdCollection.getId());
          }
        }
        admin.commit();
        for (final var collectionId : ALL_HUB_INTERNAL_COLLECTIONS) {
          final var collection = admin.getCollectionById(adminCatalog, collectionId);
          if (collection == null || collection.isDeleted()) {
            logger.error("Failed to create admin collection: {}", collectionId);
            throw illegalState("Failed to create admin collection: "+collectionId);
          }
          adminCollections.put(collectionId, collection);
        }
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

  private WriteRequest upsertAdminCollectionsRequest() {
    final WriteRequest writeRequest = new WriteRequest();
    for (String collectionId : ALL_HUB_INTERNAL_COLLECTIONS) {
      final var collection = new NakshaCollection(collectionId, adminCatalog);
      writeRequest.add(new Write().upsertCollection(collection));
    }
    return writeRequest;
  }

  private @Nullable NakshaHubConfig configSetup(
      final @NotNull NakshaContext nakshaContext,
      final @Nullable NakshaHubConfig customCfg,
      final @Nullable String configId,
      final @NotNull String adminMapId) {
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
        final var catalog = admin.getCatalogById(adminMapId);
        if (catalog == null) throw illegalState("The given admin catalog "+adminMapId+" does not exist");
        final var configCollection = admin.getCollectionById(catalog, CONFIGS);
        if (configCollection == null) throw illegalState("The given configuration collection "+CONFIGS+" does not exist");
        final WriteRequest writeCustomCfg = new WriteRequest().add(new Write().upsertFeature(configCollection, customCfg));
        final Response writeCustomCfgResponse = admin.execute(writeCustomCfg);
        if (writeCustomCfgResponse instanceof SuccessResponse) {
          admin.commit();
          return customCfg;
        } else {
          admin.rollback();
          if (writeCustomCfgResponse instanceof ErrorResponse errorResponse) {
            final NakshaError error = errorResponse.getError();
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
    final Request writeDefCfg = createFeatureRequest(adminMapId, CONFIGS, config);
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
    final Request readAdminConfigs = readFeaturesByIdsRequest(adminMapId, CONFIGS, cfgIdList);
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
    final ReadFeatures readRequest = new ReadFeatures().withCollectionId(EVENT_HANDLERS).withCatalogId(adminMapId);
    final PQuery pQueryExists = new PQuery(new Property(EXTN_ID_PROP_PATH), AnyOp.EXISTS);
    final PQuery pQueryNotNull = new PQuery(new Property(EXTN_ID_PROP_PATH), AnyOp.IS_NOT_NULL);
    final IPropertyQuery propertyQuery = new PAnd(pQueryExists, pQueryNotNull);
    readRequest.withPropertyQuery(propertyQuery);
    NakshaContext nakshaContext = NakshaContext.newInstance(NakshaHubConfig.defaultAppName());
    nakshaContext.attachToCurrentThread();
    Response response = getAdminStorage().useReadSession(SessionOptions.from(nakshaContext),
            readSession -> readSession.execute(readRequest));
    Set<String> extensionIds = new HashSet<>();
    if(response instanceof SuccessResponse successResponse) {
      final List<EventHandlerConfig> eventHandlers = extractResponseItems(successResponse, EventHandlerConfig.class);
      if(eventHandlers.isEmpty()) {
        logger.info("No relevant handlers found for Extension loading");
        return new ExtensionConfig(
                System.currentTimeMillis() + nakshaHubConfig.getExtensionConfigParams().getIntervalMs(),
                Collections.emptyList(),
                null);
      }

      for (EventHandlerConfig eventHandler : eventHandlers) {
        String extensionId = eventHandler.getExtensionId();
        if (extensionId != null && extensionId.contains(":")) {
          extensionIds.add(extensionId);
        } else {
          logger.error("Environment is missing for an extension Id");
        }
      }
    } else {
      if (response instanceof ErrorResponse errorResponse) {
        NakshaError error = errorResponse.getError();
        throw unchecked(new Exception(
                "Unable to read extension handler configurations (error code: " + error.getCode() + ")",
                error.getCause()));
      }
      throw unchecked(new Exception("Unable to read extension handler configurations (unexpected response: "
              + response + ")"));
    }

    final ExtensionConfigParams extensionConfigParams = nakshaHubConfig.getExtensionConfigParams();

    List<Extension> extList = loadExtensionConfig(extensionConfigParams.getExtensionRootPath(), extensionIds);
    return new ExtensionConfig(
        System.currentTimeMillis() + extensionConfigParams.getIntervalMs(),
        extList,
        extensionConfigParams.getWhiteListClasses());
  }

  private List<Extension> loadExtensionConfig(String extensionRootPath, Set<String> extensionIds) {
    List<Extension> extList = new ArrayList<>();
    FileClient fileClient = FileClientFactory.create(extensionRootPath);

    for (String extensionId : extensionIds) {
      String extEnv = extensionId.split(":")[0];
      String extensionIdWotEnv = extensionId.split(":")[1];
      try {
        String version = fileClient.getFileContent(
                extensionRootPath + extensionIdWotEnv + "/latest-" + extEnv.toLowerCase() + ".txt");
        String exJson = fileClient.getFileContent(extensionRootPath + extensionIdWotEnv + "/"
                + extensionIdWotEnv + "-" + version + "." + extEnv.toLowerCase() + ".json");
        Extension extension = JvmBoxingUtil.box(Base.fromJSON(exJson, FromJsonOptions.DEFAULT), Extension.class);
        extension.setEnv(extEnv);
        extList.add(extension);
      } catch (Exception e) {
        logger.error("Failed loading extension {} at {}", extensionId, extensionRootPath, e);
      }
    }
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
      Request readStorageById = readFeaturesByIdRequest(adminMapId, STORAGES, storageId);
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
