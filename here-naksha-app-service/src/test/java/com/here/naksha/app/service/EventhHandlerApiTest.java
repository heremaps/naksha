/*
 * Copyright (C) 2017-2023 HERE Europe B.V.
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
package com.here.naksha.app.service;

import static com.here.naksha.app.common.ResponseAssertions.assertThat;
import static com.here.naksha.app.common.TestUtil.loadFileOrFail;
import static com.here.naksha.app.common.TestUtil.parseJson;

import com.here.naksha.app.common.ApiTest;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeatureCollection;
import com.here.naksha.lib.core.models.naksha.EventHandler;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class EventhHandlerApiTest extends ApiTest {

  @Test
  @Order(2)
  void tc0100_testCreateEventHandler() throws Exception {
    // Test API : POST /hub/handlers
    // 1. Load test data
    final String bodyJson = loadFileOrFail("TC0100_createEventHandler/create_event_handler.json");
    final String expectedCreationResponse = loadFileOrFail("TC0100_createEventHandler/response_create_1.json");
    final String streamId = UUID.randomUUID().toString();

    // 2. Perform REST API call creating handler
    final HttpResponse<String> response = getNakshaClient().post("hub/handlers", bodyJson, streamId);

    // 3. Perform assertions
    assertThat(response)
        .hasStatus(200)
        .hasStreamIdHeader(streamId)
        .hasJsonBody(expectedCreationResponse);
  }

  @Test
  @Order(3)
  void tc0101_testDuplicateEventHandler() throws Exception {
    // Test API : POST /hub/handlers
    // 1. Load test data
    final String bodyJson = loadFileOrFail("TC0100_createEventHandler/create_event_handler.json");
    final String expectedDuplicateResponse = loadFileOrFail("TC0101_duplicateEventHandler/response_conflict.json");
    final String streamId = UUID.randomUUID().toString();

    // 2. Perform REST API call creating handler
    final HttpResponse<String> response = getNakshaClient().post("hub/handlers", bodyJson, streamId);

    // 3. Perform assertions
    assertThat(response)
        .hasStatus(409)
        .hasJsonBody(expectedDuplicateResponse, "Expecting duplicated handler in response");
  }

  @Test
  @Order(3)
  void tc0102_testCreateHandlerMissingClassName() throws Exception {
    // Test API : POST /hub/handlers
    // 1. Load test data
    final String bodyJson = loadFileOrFail("TC0102_createHandlerNoClassName/create_event_handler.json");
    final String expectedBodyPart = loadFileOrFail("TC0102_createHandlerNoClassName/response_part.json");
    final String streamId = UUID.randomUUID().toString();

    // 2. Perform REST API call
    final HttpResponse<String> response = getNakshaClient().post("hub/handlers", bodyJson, streamId);

    // 3. Perform assertions
    assertThat(response)
        .hasStatus(400)
        .hasStreamIdHeader(streamId)
        .hasJsonBody(expectedBodyPart, "Expecting failure response");
  }

  @Test
  @Order(3)
  void tc0120_testGetHandlerById() throws Exception {
    // Test API : GET /hub/handlers/{handlerId}
    // 1. Load test data
    final String expectedBodyPart = loadFileOrFail("TC0100_createEventHandler/response_create_1.json");
    final String streamId = UUID.randomUUID().toString();

    // 2. Perform REST API call
    final HttpResponse<String> response = getNakshaClient().get("hub/handlers/test-handler", streamId);

    // 3. Perform assertions
    assertThat(response)
        .hasStatus(200)
        .hasStreamIdHeader(streamId)
        .hasJsonBody(expectedBodyPart, "Expecting handler response");
  }

  @Test
  @Order(3)
  void tc0121_testGetHandlerByWrongId() throws Exception {
    // Test API : GET /hub/handlers/{handlerId}
    // 1. Load test data
    final String streamId = UUID.randomUUID().toString();

    // 2. Perform REST API call
    final HttpResponse<String> response = getNakshaClient().get("hub/handlers/not-real-handler", streamId);

    // 3. Perform assertions
    assertThat(response)
        .hasStatus(404)
        .hasStreamIdHeader(streamId);
  }

  @Test
  @Order(3)
  void tc0140_testGetHandlers() throws Exception {
    // Given: created handlers
    List<String> expectedHandlerIds = List.of("tc_140_event_handler_1", "tc_140_event_handler_2");
    final String streamId = UUID.randomUUID().toString();
    getNakshaClient()
        .post("hub/handlers", loadFileOrFail("TC0140_getHandlers/create_event_handler_1.json"), streamId);
    getNakshaClient()
        .post("hub/handlers", loadFileOrFail("TC0140_getHandlers/create_event_handler_2.json"), streamId);

    // When: Fetching all handlers
    final HttpResponse<String> response = getNakshaClient().get("hub/handlers", streamId);

    // Then: Response is successful
    assertThat(response)
        .hasStatus(200)
        .hasStreamIdHeader(streamId);

    // And: all saved handlers are returned
    List<XyzFeature> returnedXyzFeatures =
        parseJson(response.body(), XyzFeatureCollection.class).getFeatures();
    boolean allReturnedFeaturesAreEventHandlers = returnedXyzFeatures.stream()
        .allMatch(feature -> EventHandler.class.isAssignableFrom(feature.getClass()));
    Assertions.assertTrue(allReturnedFeaturesAreEventHandlers);
    List<String> eventHandlerIds =
        returnedXyzFeatures.stream().map(XyzFeature::getId).toList();
    Assertions.assertTrue(eventHandlerIds.containsAll(expectedHandlerIds));
  }

  @Test
  @Order(3)
  void tc0160_testUpdateEventHandler() throws Exception {
    // Test API : PUT /hub/handlers/{handlerId}
    // Given:
    final String updateEventHandlerJson = loadFileOrFail("TC0160_updateEventHandler/update_event_handler.json");
    final String expectedRespBody = loadFileOrFail("TC0160_updateEventHandler/response.json");
    final String streamId = UUID.randomUUID().toString();

    // When:
    final HttpResponse<String> response =
        getNakshaClient().put("hub/handlers/test-handler", updateEventHandlerJson, streamId);

    // Then:
    assertThat(response)
        .hasStatus(200)
        .hasStreamIdHeader(streamId)
        .hasJsonBody(expectedRespBody);
  }

  @Test
  @Order(3)
  void tc0161_testUpdateNonexistentEventHandler() throws Exception {
    // Test API : PUT /hub/handlers/{handlerId}
    // Given:
    final String updateEventHandlerJson =
        loadFileOrFail("TC0161_updateNonexistentEventHandler/update_event_handler.json");
    final String expectedRespBody = loadFileOrFail("TC0161_updateNonexistentEventHandler/response.json");
    final String streamId = UUID.randomUUID().toString();

    // When:
    final HttpResponse<String> response =
        getNakshaClient().put("hub/handlers/non-existent-test-handler", updateEventHandlerJson, streamId);

    // Then:
    assertThat(response)
        .hasStatus(404)
        .hasStreamIdHeader(streamId)
        .hasJsonBody(expectedRespBody);
  }

  @Test
  @Order(3)
  void tc0162_testUpdateEventHandlerWithMismatchingId() throws Exception {
    // Test API : PUT /hub/handlers/{handlerId}
    // Given:
    final String updateOtherHandlerJson =
        loadFileOrFail("TC0162_updateEventHandlerWithMismatchingId/update_event_handler.json");
    final String expectedErrorResponse = loadFileOrFail("TC0162_updateEventHandlerWithMismatchingId/response.json");
    final String streamId = UUID.randomUUID().toString();

    // When:
    final HttpResponse<String> response =
        getNakshaClient().put("hub/handlers/test-handler", updateOtherHandlerJson, streamId);

    // Then:
    assertThat(response)
        .hasStatus(400)
        .hasStreamIdHeader(streamId)
        .hasJsonBody(expectedErrorResponse);
  }
}
