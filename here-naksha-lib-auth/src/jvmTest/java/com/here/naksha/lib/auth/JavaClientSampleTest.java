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
package com.here.naksha.lib.auth;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.here.naksha.lib.auth.action.ReadCollections;
import com.here.naksha.lib.auth.action.ReadFeatures;
import com.here.naksha.lib.auth.attribute.CollectionAttributes;
import com.here.naksha.lib.auth.attribute.FeatureAttributes;
import com.here.naksha.lib.auth.attribute.NakshaAttributes;
import com.here.naksha.lib.auth.attribute.ResourceAttributes;
import com.here.naksha.lib.base.com.here.naksha.lib.auth.UserRightsMatrix;
import naksha.base.P_List;
import org.junit.jupiter.api.Test;

class JavaClientSampleTest {

  /** Demo on how to parse and deal with following ARM:
   *
   * {code @formatter:off}
   * {
   *    "someService": {
   *       "readCollections": [
   *          {
   *             "id": "someCollection",
   *             "storageId": "someStorage"
   *          },
   *          {
   *             "id": "otherCollection",
   *             "storageId": "otherStorage",
   *             "tags": ["tag1", "tag2"]
   *          }
   *       ]
   *    }
   * }
   * {@code @formatter:on}
   */
  @Test
  void armConstructionSample() {
    // Given
    AccessRightsMatrix arm = new AccessRightsMatrix()
        .withService(
            "someService",
            new ServiceAccessRights()
                .withAction(new ReadCollections()
                    .withAttributes(
                        new CollectionAttributes()
                            .id("someCollection")
                            .storageId("someStorage"),
                        new CollectionAttributes()
                            .id("otherCollection")
                            .storageId("otherStorage")
                            .tags("tag1", "tag2"))));

    // When
    ServiceAccessRights armService = arm.getService("someService");

    // Then:
    assertNotNull(armService);
    P_List<ResourceAttributes> attributes = armService.getActionAttributeMaps(ReadCollections.NAME);
    assertNotNull(attributes);
    assertEquals(2, attributes.size());
    ResourceAttributes x = attributes.get(0);
    assertEquals("someCollection", attributes.get(0).get(NakshaAttributes.ID_KEY));
    assertEquals("someStorage", attributes.get(0).get(CollectionAttributes.STORAGE_ID_KEY));
    assertEquals("otherCollection", attributes.get(1).get(NakshaAttributes.ID_KEY));
    assertEquals("otherStorage", attributes.get(1).get(CollectionAttributes.STORAGE_ID_KEY));
    assertArrayEquals(
        new String[] {"tag1", "tag2"}, (String[]) attributes.get(1).get(CollectionAttributes.TAGS_KEY));
  }

  @Test
  void urmArmFlowSample() {
    // Given: raw URM, possibly from external source
    String rawUrm = "{\n" + "   \"naksha\": {\n"
        + "      \"readFeatures\": [\n"
        + "         {\n"
        + "            \"id\": \"featureId_*\",\n"
        + "            \"storageId\": \"someStorage\"\n"
        + "         }\n"
        + "      ]\n"
        + "   }\n"
        + "}\n";

    // And:
    AccessRightsMatrix arm = new AccessRightsMatrix();
    arm.naksha()
        .withAction(new ReadFeatures()
            .withAttributes(
                new FeatureAttributes().id("featureId_121").storageId("someStorage")));

    // When: parsing rawUrm
    UserRightsMatrix urm = AuthParser.INSTANCE.parseUrm(rawUrm);

    // Then:
    assertTrue(urm.matches(arm));
  }
}
