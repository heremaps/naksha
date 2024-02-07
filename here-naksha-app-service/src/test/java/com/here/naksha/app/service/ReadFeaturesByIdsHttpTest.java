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

import com.here.naksha.app.common.ApiTest;
import com.here.naksha.app.common.NakshaTestWebClient;
import com.here.naksha.app.common.assertions.ResponseAssertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;


import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.util.UUID;

import static com.here.naksha.app.common.CommonApiTestSetup.setupSpaceAndRelatedResources;
import static com.here.naksha.app.common.TestUtil.loadFileOrFail;


class ReadFeaturesByIdsHttpTest extends ApiTest {

    private static final NakshaTestWebClient nakshaClient = new NakshaTestWebClient();

    private static final String SPACE_ID = "read_features_by_ids_http_test_space";

    @BeforeAll
    static void setup() throws URISyntaxException, IOException, InterruptedException {
        setupSpaceAndRelatedResources(nakshaClient, "ReadFeatures/ByIdsHttp/setup");
    }

    @Test
    void tc0401_testReadFeaturesForMissingIds() throws Exception {
        // Test API : GET /hub/spaces/{spaceId}/features
        // Validate empty collection getting returned for missing ids
        // Given: Features By Ids request (against configured space)
        final String idsQueryParam = "?id=missing_id";
        final String expectedBodyPart =
                loadFileOrFail("ReadFeatures/ByIdsHttp/TC0401_MissingIds/feature_response_part.json");
        String streamId = UUID.randomUUID().toString();

        // When: Create Features request is submitted to NakshaHub Space Storage instance
        HttpResponse<String> response = getNakshaClient().get("hub/spaces/" + SPACE_ID + "/features" + idsQueryParam, streamId);

        // Then: Perform assertions
        ResponseAssertions.assertThat(response)
                .hasStatus(200)
                .hasStreamIdHeader(streamId)
                .hasJsonBody(expectedBodyPart, "Get Feature response body doesn't match");
    }

}
