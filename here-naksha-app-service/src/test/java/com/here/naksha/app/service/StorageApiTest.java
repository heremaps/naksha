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

import static com.here.naksha.app.common.TestUtil.HDR_STREAM_ID;
import static com.here.naksha.app.common.TestUtil.getHeader;
import static com.here.naksha.app.common.TestUtil.loadFileOrFail;
import static com.here.naksha.app.common.TestUtil.parseJson;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.here.naksha.app.common.ApiTest;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeatureCollection;
import com.here.naksha.lib.core.models.naksha.Storage;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

public class StorageApiTest extends ApiTest {

  @Test
  @Order(1)
  void tc0001_testCreateStorages() throws Exception {
    // Test API : POST /hub/storages
    // 1. Load test data
    final String bodyJson = loadFileOrFail("TC0001_createStorage/create_storage.json");
    final String expectedBodyPart = loadFileOrFail("TC0001_createStorage/response_part.json");
    final String streamId = UUID.randomUUID().toString();

    // 2. Perform REST API call
    final HttpResponse<String> response = getNakshaClient().post("hub/storages", bodyJson, streamId);

    // 3. Perform assertions
    assertEquals(200, response.statusCode(), "ResCode mismatch");
    JSONAssert.assertEquals(
        "Expecting new storage in response", expectedBodyPart, response.body(), JSONCompareMode.LENIENT);
    assertEquals(streamId, getHeader(response, HDR_STREAM_ID), "StreamId mismatch");
  }

  @Test
  @Order(2)
  void tc0002_testCreateDuplicateStorage() throws Exception {
    // Test API : POST /hub/storages
    // 1. Load test data
    final String bodyJson = loadFileOrFail("TC0002_createDupStorage/create_storage.json");
    final String expectedBodyPart = loadFileOrFail("TC0002_createDupStorage/response_part.json");
    final String streamId = UUID.randomUUID().toString();

    // 2. Perform REST API call
    final HttpResponse<String> response = getNakshaClient().post("hub/storages", bodyJson, streamId);

    // 3. Perform assertions
    assertEquals(409, response.statusCode(), "ResCode mismatch");
    JSONAssert.assertEquals(
        "Expecting conflict error message", expectedBodyPart, response.body(), JSONCompareMode.LENIENT);
    assertEquals(streamId, getHeader(response, HDR_STREAM_ID), "StreamId mismatch");
  }

  @Test
  @Order(3)
  void tc0003_testCreateStorageMissingClassName() throws Exception {
    // Test API : POST /hub/storages
    // 1. Load test data
    final String bodyJson = loadFileOrFail("TC0003_createStorageMissingClassName/create_storage.json");
    final String expectedBodyPart = loadFileOrFail("TC0003_createStorageMissingClassName/response_part.json");
    final String streamId = UUID.randomUUID().toString();

    // 2. Perform REST API call
    final HttpResponse<String> response = getNakshaClient().post("hub/storages", bodyJson, streamId);

    // 3. Perform assertions
    assertEquals(400, response.statusCode(), "ResCode mismatch");
    JSONAssert.assertEquals(
        "Expecting failure response", expectedBodyPart, response.body(), JSONCompareMode.LENIENT);
    assertEquals(streamId, getHeader(response, HDR_STREAM_ID), "StreamId mismatch");
  }

  @Test
  @Order(2)
  void tc0004_testInvalidUrlPath() throws Exception {
    // Test API : GET /hub/invalid_storages
    final HttpResponse<String> response =
        getNakshaClient().get("hub/invalid_storages", UUID.randomUUID().toString());

    // Perform assertions
    assertEquals(404, response.statusCode(), "ResCode mismatch");
  }

  @Test
  @Order(2)
  void tc0020_testGetStorageById() throws Exception {
    // Test API : GET /hub/storages/{storageId}
    // 1. Load test data
    final String expectedBodyPart = loadFileOrFail("TC0001_createStorage/response_part.json");
    final String streamId = UUID.randomUUID().toString();

    // 2. Perform REST API call
    final HttpResponse<String> response = getNakshaClient().get("hub/storages/um-mod-dev", streamId);

    // 3. Perform assertions
    assertEquals(200, response.statusCode(), "ResCode mismatch");
    JSONAssert.assertEquals(
        "Expecting previously created storage", expectedBodyPart, response.body(), JSONCompareMode.LENIENT);
    assertEquals(streamId, getHeader(response, HDR_STREAM_ID), "StreamId mismatch");
  }

  @Test
  @Order(2)
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
  @Order(2)
  void tc0040_testGetStorages() throws Exception {
    // Given: created storages
    List<String> expectedStorageIds = List.of("tc_040_storage_1", "tc_040_storage_2");
    final String streamId = UUID.randomUUID().toString();
    getNakshaClient().post("hub/storages", loadFileOrFail("TC0040_getStorages/create_storage_1.json"), streamId);
    getNakshaClient().post("hub/storages", loadFileOrFail("TC0040_getStorages/create_storage_2.json"), streamId);

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
  @Order(2)
  void tc0060_testUpdateStorage() throws Exception {
    // Test API : PUT /hub/storages/{storageId}
    // Given:
    final String updateStorageJson = loadFileOrFail("TC0060_updateStorage/update_storage.json");
    final String expectedRespBody = loadFileOrFail("TC0060_updateStorage/response.json");
    final String streamId = UUID.randomUUID().toString();

    // When:
    final HttpResponse<String> response =
        getNakshaClient().put("hub/storages/um-mod-dev", updateStorageJson, streamId);

    // Then:
    assertEquals(200, response.statusCode());
    JSONAssert.assertEquals(expectedRespBody, response.body(), JSONCompareMode.LENIENT);
    assertEquals(streamId, getHeader(response, HDR_STREAM_ID));
  }

  @Test
  @Order(2)
  void tc0061_testUpdateNonexistentStorage() throws Exception {
    // Test API : PUT /hub/storages/{storageId}
    // Given:
    final String updateStorageJson = loadFileOrFail("TC0061_updateNonexistentStorage/update_storage.json");
    final String expectedErrorResponse = loadFileOrFail("TC0061_updateNonexistentStorage/response.json");
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
  @Order(3)
  void tc0062_testUpdateStorageWithoutClassName() throws Exception {
    // Test API : PUT /hub/storages/{storageId}
    // Given:
    final String updateStorageJson = loadFileOrFail("TC0062_updateStorageWithoutClassName/update_storage.json");
    final String expectedErrorResponse = loadFileOrFail("TC0062_updateStorageWithoutClassName/response.json");
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
  @Order(3)
  void tc0063_testUpdateStorageWithWithMismatchingId() throws Exception {
    // Test API : PUT /hub/storages/{storageId}
    // Given:
    final String bodyWithDifferentStorageId =
        loadFileOrFail("TC0063_updateStorageWithMismatchingId/update_storage.json");
    final String expectedErrorResponse = loadFileOrFail("TC0063_updateStorageWithMismatchingId/response.json");
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
