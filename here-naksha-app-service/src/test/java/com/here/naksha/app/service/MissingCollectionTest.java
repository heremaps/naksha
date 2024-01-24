package com.here.naksha.app.service;

import static com.here.naksha.app.common.CommonApiTestSetup.createHandler;
import static com.here.naksha.app.common.CommonApiTestSetup.createSpace;
import static com.here.naksha.app.common.CommonApiTestSetup.createStorage;
import static com.here.naksha.app.common.TestUtil.loadFileOrFail;
import static com.here.naksha.app.common.assertions.ResponseAssertions.assertThat;

import com.here.naksha.app.common.ApiTest;
import com.here.naksha.app.common.NakshaTestWebClient;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MissingCollectionTest extends ApiTest {

  private static final NakshaTestWebClient nakshaClient = new NakshaTestWebClient(300);

  private static final String SPACE_WITH_AUTO_CREATE_ON = "space_with_auto_create";
  private static final String SPACE_WITH_AUTO_CREATE_OFF = "space_without_auto_create";


  @BeforeAll
  static void setup() throws Exception {
    createStorage(nakshaClient, "MissingCollection/setup/create_storage.json");
    createHandler(nakshaClient, "MissingCollection/setup/create_event_handler_with_auto_create.json");
    createHandler(nakshaClient, "MissingCollection/setup/create_event_handler_without_auto_create.json");
    createSpace(nakshaClient, "MissingCollection/setup/create_space_with_auto_create.json");
    createSpace(nakshaClient, "MissingCollection/setup/create_space_without_auto_create.json");
  }

  @Test
  void tc1200_shouldFailWritingToMissingCollection() throws Exception {
    // Given: feature to create on space without autoCreate enabled
    final String bodyJson = loadFileOrFail("MissingCollection/TC1200_failOnWrite/create_features.json");
    final String expectedFailure = loadFileOrFail("MissingCollection/TC1200_failOnWrite/error_response.json");
    String streamId = UUID.randomUUID().toString();

    // When: trying to create features
    HttpResponse<String> response = getNakshaClient().post("hub/spaces/" + SPACE_WITH_AUTO_CREATE_OFF + "/features", bodyJson, streamId);

    // Then: we get en 404 due to missing collection
    assertThat(response)
        .hasStatus(404)
        .hasStreamIdHeader(streamId)
        .hasJsonBody(expectedFailure);
  }

  void tc1201_shouldFailReadingFromMissingCollection() {
    // Given: query against missing collection that would be not auto created
    String streamId = UUID.randomUUID().toString();

    // When: trying to read features

    // Then: we get en 404 due to missing collection
  }

  @Test
  void tc1202_shouldSucceedWritingToMissingCollection() throws Exception {
    // Given: feature to create on space without autoCreate enabled
    final String bodyJson = loadFileOrFail("MissingCollection/TC1202_succeedOnWrite/create_features.json");
    final String expectedResponse = loadFileOrFail("MissingCollection/TC1202_succeedOnWrite/response.json");
    String streamId = UUID.randomUUID().toString();

    // When: trying to create features
    HttpResponse<String> response = getNakshaClient().post("hub/spaces/" + SPACE_WITH_AUTO_CREATE_ON + "/features", bodyJson, streamId);

    // Then: we get en 404 due to missing collection
    assertThat(response)
        .hasStatus(200)
        .hasStreamIdHeader(streamId)
        .hasJsonBody(expectedResponse);
  }

  void tc1203_shouldSucceedReadingFromMissingCollection() {

  }
}
