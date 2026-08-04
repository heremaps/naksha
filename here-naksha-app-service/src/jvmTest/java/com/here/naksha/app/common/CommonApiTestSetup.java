package com.here.naksha.app.common;

import static com.here.naksha.app.common.TestUtil.loadFileOrFail;
import static com.here.naksha.app.common.assertions.ResponseAssertions.assertThat;
import static naksha.base.PlatformMapApi.map_get;
import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.util.UUID;

import com.here.naksha.lib.core.models.naksha.Space;
import com.here.naksha.lib.core.models.naksha.SpaceProperties;
import kotlin.Pair;
import naksha.base.Base;
import naksha.base.PlatformMap;
import naksha.model.objects.NakshaCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommonApiTestSetup {

  private static final Logger logger = LoggerFactory.getLogger(CommonApiTestSetup.class);
  private static final String COMMON_STORAGE_JSON = "create_common_storage.json";

  private static final String CREATE_HANDLER_JSON = "create_event_handler.json";
  private static final String CREATE_SPACE_JSON = "create_space.json";

  private CommonApiTestSetup() {
  }

  /**
   * Creates storage that is meant to be used by most of REST tests. This method should be run once per test suite run.
   *
   * @param nakshaClient web client that will send REST request
   * @return the identifier of the storage and the catalog.
   */
  public static @NotNull Pair<@NotNull String, @Nullable String> setupCommonStorage(NakshaTestWebClient nakshaClient) {
    try {
      logger.info("Setting up common storage from file: '{}'", COMMON_STORAGE_JSON);
      return createStorage(nakshaClient, COMMON_STORAGE_JSON);
    } catch (URISyntaxException | IOException | InterruptedException e) {
      throw new RuntimeException("Unable to setup common storage", e);
    }
  }

  /**
   * Convenience method that creates resources needed for feature-related operations (Storage, Handler, Space) Client needs to supply
   * directory which contains corresponding json file for each of the resources (`create_storage.json`,`create_event_handler.json`,
   * `create_space.json`)
   *
   * @param nakshaClient Naksha http client used for creating resource via REST API
   * @param setupDir     subdirectory of 'src/jvmTest/resources/unit_test_data/' that contains resource definition in json format
   * @return the identifier of the underlying collection of the created space, if the space has an underlying collection.
   */
  public static @Nullable String setupHandlerAndSpace(NakshaTestWebClient nakshaClient, String setupDir) {
    try {
      createHandler(nakshaClient, setupDir + "/" + CREATE_HANDLER_JSON);
      return createSpace(nakshaClient, setupDir + "/" + CREATE_SPACE_JSON);
    } catch (URISyntaxException | IOException | InterruptedException e) {
      throw new RuntimeException("Unable to run setup for dir: " + setupDir, e);
    }
  }

  /** Creates a new space and returns the identifier of the underlying collection of the space, if the sapce has any. */
  public static @Nullable String createSpace(NakshaTestWebClient nakshaClient, String spaceJsonFilePath)
      throws URISyntaxException, IOException, InterruptedException {
    final var requestString = loadFileOrFail(spaceJsonFilePath);
    final var responseString = createAdminEntity(nakshaClient, "hub/spaces", requestString);
    final PlatformMap map = assertInstanceOf(PlatformMap.class, Base.fromJSON(responseString));
    final Space space = Base.javaProxy(map, Space.class);
    assertNotNull(space);
    final SpaceProperties spaceProperties = space.getProperties();
    assertNotNull(spaceProperties);
    final NakshaCollection collection = spaceProperties.getCollection();
    return collection != null ? collection.getId() : null;
  }

  /**
   * Creates a new storage and returns the identifier of the storage and the catalog.
   * @param nakshaClient the client to use.
   * @param storageJsonFilePath the file-path of the request.
   * @return the database-id <i>(aka the storage-id)</i> and the optional catalog-id <i>(aka map-id)</i>, if the storage request sets up a default catalog <i>(does not always happen for view storages)</i>.
   */
  public static @NotNull Pair<@NotNull String, @Nullable String> createStorage(NakshaTestWebClient nakshaClient, String storageJsonFilePath)
      throws URISyntaxException, IOException, InterruptedException {
    final var requestString = loadFileOrFail(storageJsonFilePath);
    final var responseString = createAdminEntity(nakshaClient, "hub/storages", requestString);
    final PlatformMap storage = assertInstanceOf(PlatformMap.class, Base.fromJSON(responseString));

    final String storageId = assertInstanceOf(String.class, map_get(storage, "id"));
    assertNotNull("Missing 'id' property in response", storageId);

    final PlatformMap properties = assertInstanceOf(PlatformMap.class, map_get(storage, "properties"));
    String catalogId = null;
    // Note: Views configure the schema/catalog in `properties.dbConfig.schema`
    Object raw = map_get(properties, "dbConfig");
    if (raw instanceof PlatformMap dbConfig) {
      raw =  map_get(dbConfig, "schema");
      if (raw instanceof String s) catalogId = s;
    }
    // Note: Spaces configure the schema/catalog in `properties.schema`
    if (catalogId == null) {
      raw = map_get(properties, "schema");
      if (raw instanceof String s) catalogId = s;
    }
    // Note: There are situations in which we simply have no clue what the underlying schema/catalog is.
    //       This is really suboptimal for test cases, but technically not an issue as a space has
    //       generally nothing to do with a storage, it can be fully independent of a storage!
    return new Pair<>(storageId, catalogId);
  }

  /** Creates a new handler and returns the identifier of the handler. */
  public static @NotNull String createHandler(NakshaTestWebClient nakshaClient, String handlerJsonFilePath)
      throws URISyntaxException, IOException, InterruptedException {
    final var requestString = loadFileOrFail(handlerJsonFilePath);
    final var responseString = createAdminEntity(nakshaClient, "hub/handlers", requestString);
    final PlatformMap map = assertInstanceOf(PlatformMap.class, Base.fromJSON(responseString));
    final String id = assertInstanceOf(String.class, map_get(map, "id"));
    assertNotNull(id);
    return id;
  }

  // Returns the response.
  private static @NotNull String createAdminEntity(NakshaTestWebClient nakshaClient, String nakshaResourcePath, String requestString)
      throws URISyntaxException, IOException, InterruptedException {
    HttpResponse<String> response = nakshaClient.post(nakshaResourcePath, requestString, UUID.randomUUID().toString());
    assertThat(response).hasStatus(200);
    return response.body();
  }

}
