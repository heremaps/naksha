package com.here.naksha.app.common;

import static com.here.naksha.app.common.TestUtil.loadFileOrFail;
import static com.here.naksha.app.common.assertions.ResponseAssertions.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.util.UUID;

import com.here.naksha.lib.core.models.naksha.Space;
import com.here.naksha.lib.core.models.naksha.SpaceProperties;
import kotlin.Pair;
import naksha.base.Platform;
import naksha.base.PlatformMap;
import naksha.base.PlatformMapApi;
import naksha.model.objects.NakshaCollection;
import org.jetbrains.annotations.NotNull;
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
  public static @NotNull Pair<@NotNull String, @NotNull String> setupCommonStorage(NakshaTestWebClient nakshaClient) {
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
   * @return the identifier of the underlying collection of the created space.
   */
  public static @NotNull String setupHandlerAndSpace(NakshaTestWebClient nakshaClient, String setupDir) {
    try {
      createHandler(nakshaClient, setupDir + "/" + CREATE_HANDLER_JSON);
      return createSpace(nakshaClient, setupDir + "/" + CREATE_SPACE_JSON);
    } catch (URISyntaxException | IOException | InterruptedException e) {
      throw new RuntimeException("Unable to run setup for dir: " + setupDir, e);
    }
  }

  /** Creates a new space and returns the identifier of the underlying collection of the space. */
  public static @NotNull String createSpace(NakshaTestWebClient nakshaClient, String spaceJsonFilePath)
      throws URISyntaxException, IOException, InterruptedException {
    final var requestString = loadFileOrFail(spaceJsonFilePath);
    final var responseString = createAdminEntity(nakshaClient, "hub/spaces", requestString);
    final PlatformMap map = assertInstanceOf(PlatformMap.class, Platform.fromJSON(responseString));
    final Space space = Platform.javaProxy(map, Space.class);
    assertNotNull(space);
    final SpaceProperties spaceProperties = space.getProperties();
    assertNotNull(spaceProperties);
    final NakshaCollection collection = spaceProperties.getCollection();
    assertNotNull(collection);
    final String id = collection.getId();
    assertNotNull(id);
    return id;
  }

  /** Creates a new storage and returns the identifier of the storage and the catalog. */
  public static @NotNull Pair<@NotNull String, @NotNull String> createStorage(NakshaTestWebClient nakshaClient, String storageJsonFilePath)
      throws URISyntaxException, IOException, InterruptedException {
    final var requestString = loadFileOrFail(storageJsonFilePath);
    final var responseString = createAdminEntity(nakshaClient, "hub/storages", requestString);
    final PlatformMap storage = assertInstanceOf(PlatformMap.class, Platform.fromJSON(responseString));

    final String storageId = assertInstanceOf(String.class, PlatformMapApi.map_get(storage, "id"));
    assertNotNull(storageId);

    final PlatformMap properties = assertInstanceOf(PlatformMap.class, PlatformMapApi.map_get(storage, "properties"));
    final String catalogId = assertInstanceOf(String.class, PlatformMapApi.map_get(properties, "schema"));
    assertNotNull(catalogId);

    return new Pair<>(storageId, catalogId);
  }

  /** Creates a new handler and returns the identifier of the handler. */
  public static @NotNull String createHandler(NakshaTestWebClient nakshaClient, String handlerJsonFilePath)
      throws URISyntaxException, IOException, InterruptedException {
    final var requestString = loadFileOrFail(handlerJsonFilePath);
    final var responseString = createAdminEntity(nakshaClient, "hub/handlers", requestString);
    final PlatformMap map = assertInstanceOf(PlatformMap.class, Platform.fromJSON(responseString));
    final String id = assertInstanceOf(String.class, PlatformMapApi.map_get(map, "id"));
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
