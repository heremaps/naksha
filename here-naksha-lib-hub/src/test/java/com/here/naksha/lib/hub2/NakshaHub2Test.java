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
package com.here.naksha.lib.hub2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.here.naksha.lib.core.IEventHandler;
import com.here.naksha.lib.core.NakshaAdminCollection;
import com.here.naksha.lib.hub2.admin.AdminStorage;
import com.here.naksha.lib.hub2.space.SpaceStorage;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class NakshaHub2Test {

  @Mock
  private AdminStorage adminStorage;

  @Mock
  private SpaceStorage spaceStorage;

  @BeforeEach
  void setup() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void shouldReturnSuppliedStorages() {
    // Given: Naksha Hub instance with admin and space storages supplied
    NakshaHub2 hub = new NakshaHub2(adminStorage, spaceStorage);

    // Then: Corresponding Admin Storage is retrievable
    assertEquals(hub.getAdminStorage(), adminStorage);

    // And: Corresponding Space Storage is retrievable
    assertEquals(hub.getSpaceStorage(), spaceStorage);
  }

  @Test
  void shouldConfigureVirtualSpacesForSpaceStorage() {
    // When: Instantiating new Naksha Hub instance
    NakshaHub2 hub = new NakshaHub2(adminStorage, spaceStorage);

    // Then: Space storage should be configured with 'some' virtual spaces
    ArgumentCaptor<Map<String, List<IEventHandler>>> virtualSpaceCaptor = ArgumentCaptor.forClass(Map.class);
    verify(spaceStorage, times(1)).setVirtualSpaces(virtualSpaceCaptor.capture());

    // And: Virtual spaces passed to space storage should include all admin spaces
    Set<String> expectedAdminSpaceIds = new HashSet<>(NakshaAdminCollection.ALL);
    Set<String> passedSpaceIds = virtualSpaceCaptor.getValue().keySet();
    assertEquals(expectedAdminSpaceIds, passedSpaceIds);
  }

  @Test
  void shouldConfigureEventPipelineFactoryForSpaceStorage() {
    // When: Instantiating new Naksha Hub instance
    NakshaHub2 hub = new NakshaHub2(adminStorage, spaceStorage);

    // Then: Space storage should be configured with new virtual spaces
    verify(spaceStorage, times(1)).setEventPipelineFactory(new NakshaEventPipelineFactory(hub));
  }
}
