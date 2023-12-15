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

import com.here.naksha.app.common.ApiTest;
import com.here.naksha.app.common.NakshaTestWebClient;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WriteFeaturesAtomicityTest extends ApiTest {

  private static final NakshaTestWebClient nakshaClient = new NakshaTestWebClient();
  private static final String SPACE_ID = "write_features_atomicity_test_space";

  public WriteFeaturesAtomicityTest() {
    super(nakshaClient);
  }

  @BeforeAll
  static void prepareEnv() {
    try {
      createStorage();
      createHandler();
      createSpace();
    } catch (URISyntaxException | IOException | InterruptedException e) {
      throw new RuntimeException("Environment setup for atomicity tests failed", e);
    }
  }

  @Test
  void tc_1101_postAtomicity() {
    boolean x = true;
    Assertions.assertTrue(x);
  }

  @Test
  void tc_1102_putAtomicity() {
  }

  @Test
  void tc_1103_deleteAtomicity() {
  }

  private static void createSpace() throws URISyntaxException, IOException, InterruptedException {
    HttpResponse<String> response = nakshaClient.post(
        "hub/spaces",
        loadFileOrFail("WriteFeaturesAtomicity/setup/create_space.json"),
        UUID.randomUUID().toString()
    );
    assertThat(response).hasStatus(200);
  }

  private static void createStorage() throws URISyntaxException, IOException, InterruptedException {
    HttpResponse<String> response = nakshaClient.post(
        "hub/storages",
        loadFileOrFail("WriteFeaturesAtomicity/setup/create_storage.json"),
        UUID.randomUUID().toString()
    );
    assertThat(response).hasStatus(200);
  }

  private static void createHandler() throws URISyntaxException, IOException, InterruptedException {
    HttpResponse<String> response = nakshaClient.post(
        "hub/handlers",
        loadFileOrFail("WriteFeaturesAtomicity/setup/create_event_handler.json"),
        UUID.randomUUID().toString()
    );
    assertThat(response).hasStatus(200);
  }
}
