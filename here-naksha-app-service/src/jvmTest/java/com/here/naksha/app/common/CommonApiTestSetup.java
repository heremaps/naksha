package com.here.naksha.app.common;

import static com.here.naksha.app.common.TestUtil.loadFileOrFail;
import static com.here.naksha.app.common.assertions.ResponseAssertions.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.util.UUID;
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
   */
  public static void setupCommonStorage(NakshaTestWebClient nakshaClient) {
    try {
      logger.info("Setting up common storage from file: '{}'", COMMON_STORAGE_JSON);
      createStorage(nakshaClient, COMMON_STORAGE_JSON);
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
   * @param setupDir     subdirectory of 'src/test/resources/unit_test_data/' that contains resource definition in json format
   */
  public static void setupSpaceAndRelatedResources(NakshaTestWebClient nakshaClient, String setupDir) {
    try {
      createHandler(nakshaClient, setupDir + "/" + CREATE_HANDLER_JSON);
      createSpace(nakshaClient, setupDir + "/" + CREATE_SPACE_JSON);
    } catch (URISyntaxException | IOException | InterruptedException e) {
      throw new RuntimeException("Unable to run setup for dir: " + setupDir, e);
    }
  }

  public static void createSpace(NakshaTestWebClient nakshaClient, String spaceJsonFilePath)
      throws URISyntaxException, IOException, InterruptedException {
    createAdminEntity(nakshaClient, "hub/spaces", spaceJsonFilePath);
  }

  public static void createStorage(NakshaTestWebClient nakshaClient, String storageJsonFilePath)
      throws URISyntaxException, IOException, InterruptedException {
    createAdminEntity(nakshaClient, "hub/storages", storageJsonFilePath);
  }

  public static void createHandler(NakshaTestWebClient nakshaClient, String handlerJsonFilePath)
      throws URISyntaxException, IOException, InterruptedException {
    createAdminEntity(nakshaClient, "hub/handlers", handlerJsonFilePath);
  }

  private static void createAdminEntity(NakshaTestWebClient nakshaClient, String nakshaResourcePath, String jsonFilePath)
      throws URISyntaxException, IOException, InterruptedException {
    HttpResponse<String> response = nakshaClient.post(
        nakshaResourcePath,
        loadFileOrFail(jsonFilePath),
        UUID.randomUUID().toString()
    );
    assertThat(response).hasStatus(200);
  }

}
