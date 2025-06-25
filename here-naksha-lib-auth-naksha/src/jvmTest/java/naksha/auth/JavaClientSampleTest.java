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
package naksha.auth;

import static naksha.base.Platform.forClass;
import static org.junit.jupiter.api.Assertions.*;

import naksha.auth.naksha.CollectionParams;
import naksha.auth.naksha.NakshaOps;
import naksha.base.Platform;
import naksha.model.NakshaContext;
import naksha.model.objects.NakshaCollection;
import naksha.model.objects.NakshaMap;
import naksha.model.objects.NakshaStorage;
import org.junit.jupiter.api.Test;

class JavaClientSampleTest {

  /**
   * Demo on how to parse and deal with <i>User Rights Matrix</i> and <i>Access Matrix</i>.
   * <p>
   * This demo assumes the user does have the following URM <i>(User Rights Matrix)</i>
   * <pre>{@code {
   *    "naksha": {
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
   * }</pre>
   * And tries to read the collection with the {@code id} "someCollection".
   */
  @Test
  void armConstructionSample() {
    // ------------------------------------------------------------------------------------------------------------------------------------
    // The following code is normally done by Naksha-Hub:

    // Parse the URM, for example as provided in HTTP header of the request.
    final String urmJson = "{\n" +
        "   \"naksha\": {\n" +
        "      \"readCollections\": [\n" +
        "         {\n" +
        "            \"id\": \"someCollection\",\n" +
        "            \"storageId\": \"someStorage\"\n" +
        "         },\n" +
        "         {\n" +
        "            \"id\": \"otherCollection\",\n" +
        "            \"storageId\": \"otherStorage\",\n" +
        "            \"tags\": [\"tag1\", \"tag2\"]\n" +
        "         }\n" +
        "      ]\n" +
        "   }\n" +
        "}";
    final UserRightsMatrix userRightsMatrix = Platform.fromJson(urmJson, forClass(UserRightsMatrix.class));
    assertNotNull(userRightsMatrix);

    // Attach the URM, and other information read from HTTP header, into the context:
    final NakshaContext context = NakshaContext.currentContext();
    context.setAppId("someApp");
    context.setAuthor("someUser");
    context.setUrm(userRightsMatrix);

    // ------------------------------------------------------------------------------------------------------------------------------------
    // Now, assume you write some code that should test if the user has some rights.
    // Assume the user wants to read the collection with id "someCollection", in "someMap", in "someStorage".
    // You should have the storage, map, and collection the user wants to access at hand:
    final NakshaStorage nakshaStorage = new NakshaStorage().withId("someStorage");
    final NakshaMap nakshaMap = new NakshaMap("someMap");
    final NakshaCollection nakshaCollection = new NakshaCollection("someCollection", "someMap");

    // Then you create the operations the user wants to perform:
    final NakshaOps ops = new NakshaOps();
    ops.getReadCollections().add(
        new CollectionParams(nakshaCollection, nakshaMap, nakshaStorage)
    );

    // The client that prepared above operation description, now tests if the current users URM allows this.
    final UserRightsMatrix urm = NakshaContext.currentContext().getUrm();
    assertNotNull(urm);
    assertTrue( urm.matches(ops) );
  }
}
