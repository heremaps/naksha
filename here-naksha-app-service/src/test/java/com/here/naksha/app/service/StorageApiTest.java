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
import static com.here.naksha.app.common.TestUtil.HDR_STREAM_ID;
import static com.here.naksha.app.common.TestUtil.getHeader;
import static com.here.naksha.app.common.TestUtil.loadFileOrFail;
import static com.here.naksha.app.common.TestUtil.parseJson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.here.naksha.app.common.ApiTest;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeatureCollection;
import com.here.naksha.lib.core.models.naksha.Storage;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

class StorageApiTest extends ApiTest {

  @Test
  void tc0001_testCreateStorages() throws Exception {
    // Test API : POST /hub/storages
    // 1. Load test data
    final String bodyJson = loadFileOrFail("StorageApi/TC0001_createStorage/create_storage.json");
    final String expectedBodyPart = loadFileOrFail("StorageApi/TC0001_createStorage/response_part.json");
    final String streamId = UUID.randomUUID().toString();

    // 2. Perform REST API call
    final HttpResponse<String> response = getNakshaClient().post("hub/storages", bodyJson, streamId);

    // 3. Perform assertions
    assertThat(response)
        .hasStatus(200)
        .hasStreamIdHeader(streamId)
        .hasJsonBody(expectedBodyPart, "Expecting new storage in response");
  }

  @Test
  void tc0002_testCreateDuplicateStorage() throws Exception {
    // Test API : POST /hub/storages
    // Given: registered storage
    final String bodyJson = loadFileOrFail("StorageApi/TC0002_createDupStorage/create_storage.json");
    final String expectedBodyPart = loadFileOrFail("StorageApi/TC0002_createDupStorage/response_part.json");
    final String streamId = UUID.randomUUID().toString();
    getNakshaClient().post("hub/storages", bodyJson, streamId);

    // When: registering storage once again
    final HttpResponse<String> response = getNakshaClient().post("hub/storages", bodyJson, streamId);

    // Then: response indicates conflict
    assertThat(response)
        .hasStatus(409)
        .hasStreamIdHeader(streamId)
        .hasJsonBody(expectedBodyPart, "Expecting conflict error message");
  }

  @Test
  void tc0003_testCreateStorageMissingClassName() throws Exception {
    // Test API : POST /hub/storages
    // 1. Load test data
    final String bodyJson = loadFileOrFail("StorageApi/TC0003_createStorageMissingClassName/create_storage.json");
    final String expectedBodyPart = loadFileOrFail("StorageApi/TC0003_createStorageMissingClassName/response_part.json");
    final String streamId = UUID.randomUUID().toString();

    // 2. Perform REST API call
    final HttpResponse<String> response = getNakshaClient().post("hub/storages", bodyJson, streamId);

    // 3. Perform assertions
    assertThat(response)
        .hasStatus(400)
        .hasStreamIdHeader(streamId)
        .hasJsonBody(expectedBodyPart, "Expecting error response");
  }

  @Test
  void tc0004_testInvalidUrlPath() throws Exception {
    // Test API : GET /hub/invalid_storages
    final HttpResponse<String> response =
        getNakshaClient().get("hub/invalid_storages", UUID.randomUUID().toString());

    // Perform assertions
    assertThat(response).hasStatus(404);
  }

  @Test
  void tc0020_testGetStorageById() throws Exception {
    // Test API : GET /hub/storages/{storageId}
    // GivenL registered storage
    final String expectedResponse = loadFileOrFail("StorageApi/TC0020_getStorageById/response.json");
    final String streamId = UUID.randomUUID().toString();
    final String createJson = loadFileOrFail("StorageApi/TC0020_getStorageById/create_storage.json");
    getNakshaClient().post("hub/storages", createJson, streamId);

    // When: fetching registered storage by id
    final HttpResponse<String> response = getNakshaClient().get("hub/storages/tc_0020_test_storage", streamId);

    // Then: registered storage is returned
    assertThat(response)
        .hasStatus(200)
        .hasStreamIdHeader(streamId)
        .hasJsonBody(expectedResponse, "Expecting previously created storage");
  }

  @Test
  void tc0021_testGetStorageByWrongId() throws Exception {
    // Test API : GET /hub/storages/{storageId}
    // 1. Load test data
    final String streamId = UUID.randomUUID().toString();

    // 2. Perform REST API call
    final HttpResponse<String> response = getNakshaClient().get("hub/storages/nothingness", streamId);

    // 3. Perform assertions
    assertEquals(404, response.statusCode(), "ResCode mismatch");
    assertEquals(streamId, getHeader(response, HDR_STREAM_ID), "StreamId mismatch");
  }

  @Test
  void tc0022_testGetStorageNoPasswords() throws Exception {
    // Test API : GET /hub/storages/{storageId}
    // 1. Load test data, create storage
    final String bodyJson = loadFileOrFail("StorageApi/TC0022_getStorageNoPasswords/create_storage.json");
    final String streamId = UUID.randomUUID().toString();
    HttpResponse<String> response = getNakshaClient().post("hub/storages", bodyJson, streamId);
    assertEquals(200, response.statusCode(), "ResCode mismatch");

    // 2. Perform REST API call
    response = getNakshaClient().get("hub/storages/storage-for-hiding-password-test", streamId);

    // 3. Perform assertions
    assertEquals(200, response.statusCode(), "ResCode mismatch");
    final JsonNode jsonNode = new ObjectMapper().readTree(response.body());
    assertFalse(jsonNode.get("properties").get("master").has("password"));
    for (JsonNode node : jsonNode.get("properties").get("reader")) {
      assertFalse(node.has("password"));
    }
    assertEquals(streamId, getHeader(response, HDR_STREAM_ID), "StreamId mismatch");
  }

  @Test
  void tc0040_testGetStorages() throws Exception {
    // Given: created storages
    List<String> expectedStorageIds = List.of("tc_040_storage_1", "tc_040_storage_2");
    final String streamId = UUID.randomUUID().toString();
    getNakshaClient().post("hub/storages", loadFileOrFail("StorageApi/TC0040_getStorages/create_storage_1.json"), streamId);
    getNakshaClient().post("hub/storages", loadFileOrFail("StorageApi/TC0040_getStorages/create_storage_2.json"), streamId);

    // When: Fetching all storages
    final HttpResponse<String> response = getNakshaClient().get("hub/storages", streamId);

    // Then: Expect all saved storages are returned
    assertEquals(200, response.statusCode(), "ResCode mismatch");
    assertEquals(streamId, getHeader(response, HDR_STREAM_ID), "StreamId mismatch");
    List<XyzFeature> returnedXyzFeatures =
        parseJson(response.body(), XyzFeatureCollection.class).getFeatures();
    boolean allReturnedFeaturesAreStorages =
        returnedXyzFeatures.stream().allMatch(feature -> Storage.class.isAssignableFrom(feature.getClass()));
    Assertions.assertTrue(allReturnedFeaturesAreStorages);
    List<String> storageIds =
        returnedXyzFeatures.stream().map(XyzFeature::getId).toList();
    Assertions.assertTrue(storageIds.containsAll(expectedStorageIds));
  }

  @Test
  void tc0060_testUpdateStorage() throws Exception {
    // Test API : PUT /hub/storages/{storageId}
    // Given: registered storage
    final String updateStorageJson = loadFileOrFail("StorageApi/TC0060_updateStorage/update_storage.json");
    final String expectedRespBody = loadFileOrFail("StorageApi/TC0060_updateStorage/response.json");
    final String createJson = loadFileOrFail("StorageApi/TC0060_updateStorage/create_storage.json");
    final String streamId = UUID.randomUUID().toString();
    getNakshaClient().post("hub/storages", createJson, streamId);

    // When: updating existing storage
    final HttpResponse<String> response =
        getNakshaClient().put("hub/storages/tc_0060_test_storage", updateStorageJson, streamId);

    // Then: storage got updated
    assertEquals(200, response.statusCode());
    JSONAssert.assertEquals(expectedRespBody, response.body(), JSONCompareMode.LENIENT);
    assertEquals(streamId, getHeader(response, HDR_STREAM_ID));
  }

  @Test
  void tc0061_testUpdateNonexistentStorage() throws Exception {
    // Test API : PUT /hub/storages/{storageId}
    // Given:
    final String updateStorageJson = loadFileOrFail("StorageApi/TC0061_updateNonexistentStorage/update_storage.json");
    final String expectedErrorResponse = loadFileOrFail("StorageApi/TC0061_updateNonexistentStorage/response.json");
    final String streamId = UUID.randomUUID().toString();

    // When:
    final HttpResponse<String> response =
        getNakshaClient().put("hub/storages/this-id-does-not-exist", updateStorageJson, streamId);

    // Then:
    assertEquals(404, response.statusCode());
    JSONAssert.assertEquals(expectedErrorResponse, response.body(), JSONCompareMode.LENIENT);
    assertEquals(streamId, getHeader(response, HDR_STREAM_ID));
  }

  @Test
  void tc0062_testUpdateStorageWithoutClassName() throws Exception {
    // Test API : PUT /hub/storages/{storageId}
    // Given:
    final String updateStorageJson = loadFileOrFail("StorageApi/TC0062_updateStorageWithoutClassName/update_storage.json");
    final String expectedErrorResponse = loadFileOrFail("StorageApi/TC0062_updateStorageWithoutClassName/response.json");
    final String streamId = UUID.randomUUID().toString();

    // When:
    final HttpResponse<String> response =
        getNakshaClient().put("hub/storages/um-mod-dev", updateStorageJson, streamId);

    // Then:
    assertEquals(400, response.statusCode());
    JSONAssert.assertEquals(expectedErrorResponse, response.body(), JSONCompareMode.LENIENT);
    assertEquals(streamId, getHeader(response, HDR_STREAM_ID));
  }

  @Test
  void tc0063_testUpdateStorageWithWithMismatchingId() throws Exception {
    // Test API : PUT /hub/storages/{storageId}
    // Given:
    final String bodyWithDifferentStorageId =
        loadFileOrFail("StorageApi/TC0063_updateStorageWithMismatchingId/update_storage.json");
    final String expectedErrorResponse = loadFileOrFail("StorageApi/TC0063_updateStorageWithMismatchingId/response.json");
    final String streamId = UUID.randomUUID().toString();

    // When:
    final HttpResponse<String> response =
        getNakshaClient().put("hub/storages/not-really-um-mod-dev", bodyWithDifferentStorageId, streamId);

    // Then:
    assertEquals(400, response.statusCode());
    JSONAssert.assertEquals(expectedErrorResponse, response.body(), JSONCompareMode.LENIENT);
    assertEquals(streamId, getHeader(response, HDR_STREAM_ID));
  }
}
