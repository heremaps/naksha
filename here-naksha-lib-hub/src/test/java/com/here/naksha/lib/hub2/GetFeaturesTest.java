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

import static com.here.naksha.lib.common.FeatureReaderUtil.fetchSingleFeatureFromSpace;
import static com.here.naksha.lib.common.SampleNakshaContext.NAKSHA_CONTEXT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.here.naksha.lib.common.TestFileLoader;
import com.here.naksha.lib.core.NakshaAdminCollection;
import com.here.naksha.lib.core.models.storage.IAdvancedReadResult;
import com.here.naksha.lib.core.models.storage.ReadFeatures;
import com.here.naksha.lib.core.models.storage.ReadResult;
import com.here.naksha.lib.core.storage.IReadSession;
import com.here.naksha.lib.hub2.admin.AdminStorage;
import com.here.naksha.lib.hub2.space.SpaceStorage;
import java.util.Iterator;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class GetFeaturesTest {

  NakshaHub2 hub;

  @Mock
  AdminStorage adminStorage;

  @Mock
  SpaceStorage spaceStorage;

  @BeforeEach
  void setup() {
    MockitoAnnotations.openMocks(this);
    hub = new NakshaHub2(adminStorage, spaceStorage);
  }

  @Test
  void shouldReturnFeatures() {
    // Given: expected result
    String expectedStorages = TestFileLoader.loadFileOrFail("TC0001_getStorages/body_part.json");

    // And: Sample request
    ReadFeatures readFeaturesRequest = new ReadFeatures(NakshaAdminCollection.STORAGES);

    // And: space storage returning expected result
    IReadSession readSession = Mockito.mock(IReadSession.class);
    when(spaceStorage.newReadSession(eq(NAKSHA_CONTEXT), anyBoolean())).thenReturn(readSession);
    when(readSession.execute(readFeaturesRequest)).thenReturn(new SimpleStringResult(expectedStorages));

    // When: fetching feature from space storage via hub
    String fetchedFeature = fetchSingleFeatureFromSpace(hub, NAKSHA_CONTEXT, readFeaturesRequest, String.class);

    // Then: expected result matches fetched feature
    assertEquals(expectedStorages, fetchedFeature);
  }

  static class SimpleStringResult extends ReadResult<String> {

    private final String value;

    public SimpleStringResult(String value) {
      super(String.class);
      this.value = value;
    }

    @Override
    public IAdvancedReadResult<String> advanced() {
      return null;
    }

    @Override
    protected void onFeatureTypeChange() {
      // not needed in test impl
    }

    @NotNull
    @Override
    public Iterator<String> iterator() {
      return List.of(value).iterator();
    }
  }
}
