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
package com.here.naksha.app.service;

import static com.here.naksha.app.common.CommonApiTestSetup.createHandler;
import static com.here.naksha.app.common.CommonApiTestSetup.createSpace;
import static com.here.naksha.app.common.TestUtil.loadFileOrFail;
import static com.here.naksha.app.common.assertions.ResponseAssertions.assertThat;

import com.here.naksha.app.common.ApiTest;
import com.here.naksha.app.common.NakshaTestWebClient;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifies that violations returned from a MOM10 validation dry-run go through the same post-processing as regular
 * features, i.e. that pre-MOM10 namespaces ({@code @ns:com:here:mom:meta} / {@code @ns:com:here:mom:delta}) are
 * stripped from violations too, not only from features.
 */
public class Mom10ViolationsPostProcessingTest extends ApiTest {

  private static final NakshaTestWebClient nakshaClient = new NakshaTestWebClient();

  private static final String SPACE_ID = "local-space-4-mom10-val-dry-run";

  @BeforeAll
  static void setup() throws URISyntaxException, IOException, InterruptedException {
    createHandler(nakshaClient, "Mom10ValDryRun/setup/create_context_loader_handler.json");
    createHandler(nakshaClient, "Mom10ValDryRun/setup/create_validation_handler.json");
    createHandler(nakshaClient, "Mom10ValDryRun/setup/create_endorsement_handler.json");
    createHandler(nakshaClient, "Mom10ValDryRun/setup/create_echo_handler.json");
    createSpace(nakshaClient, "Mom10ValDryRun/setup/create_space.json");
  }

  @Test
  void shouldStripPreMom10NamespacesFromViolations() throws Exception {
    // Test API : POST /hub/spaces/{spaceId}/features
    // Given: PUT features request that (due to an odd feature count) triggers mock MOM10 violations
    final String streamId = UUID.randomUUID().toString();
    final String bodyJson = loadFileOrFail("Mom10ValDryRun/testViolationsPostProcessingResult/upsert_features.json");
    final String expectedBodyPart =
        loadFileOrFail("Mom10ValDryRun/testViolationsPostProcessingResult/feature_response_part.json");

    // When: Request is submitted to NakshaHub Space Storage instance
    final HttpResponse<String> response =
        nakshaClient.post("hub/spaces/" + SPACE_ID + "/features", bodyJson, streamId);

    // Then: Violations are present, and were post-processed just like the features were
    assertThat(response)
        .hasStatus(200)
        .hasStreamIdHeader(streamId)
        .hasJsonBody(expectedBodyPart, "Validation dry-run response body doesn't match")
        .hasViolationsWithoutPreMom10Namespaces();
  }
}
