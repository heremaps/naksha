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
package com.here.naksha.lib.hub2.admin;

import static com.here.naksha.lib.common.SampleNakshaContext.NAKSHA_CONTEXT;
import static com.here.naksha.lib.core.models.storage.EWriteOp.INSERT;
import static com.here.naksha.lib.hub2.admin.AdminStorageTest.TestAdminStorage.DEFAULT_STORAGE_TEST_CLASS_NAME;
import static com.here.naksha.lib.hub2.admin.AdminStorageTest.TestAdminStorage.DEFAULT_STORAGE_TEST_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.here.naksha.lib.core.NakshaAdminCollection;
import com.here.naksha.lib.core.NakshaContext;
import com.here.naksha.lib.core.models.naksha.Storage;
import com.here.naksha.lib.core.models.storage.StorageCollection;
import com.here.naksha.lib.core.models.storage.SuccessResult;
import com.here.naksha.lib.core.models.storage.WriteCollections;
import com.here.naksha.lib.core.models.storage.WriteFeatures;
import com.here.naksha.lib.core.models.storage.WriteOp;
import com.here.naksha.lib.core.models.storage.WriteRequest;
import com.here.naksha.lib.core.storage.IStorage;
import com.here.naksha.lib.core.storage.IWriteSession;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class AdminStorageTest {

  @Mock
  IStorage physicalAdminStorage;

  @BeforeEach
  void setup() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void shouldCreateAdminCollections() {
    // Given: Physical admin storage that's capable of creating write sessions
    IWriteSession physicalWriteSession = mock(IWriteSession.class);
    when(physicalAdminStorage.newWriteSession(NAKSHA_CONTEXT, true)).thenReturn(physicalWriteSession);

    // And: all write requests to physical storage are successful
    when(physicalWriteSession.execute(any(WriteRequest.class))).thenReturn(new SuccessResult());

    // When: creating new Admin Storage
    new TestAdminStorage(physicalAdminStorage, NAKSHA_CONTEXT);

    // Then: There was a write request on physical admin storage
    ArgumentCaptor<WriteRequest> adminWriteRequests = ArgumentCaptor.forClass(WriteRequest.class);
    verify(physicalWriteSession, times(2)).execute(adminWriteRequests.capture());

    // And: First write requests was about writing collections
    WriteRequest firstWriteRequest = adminWriteRequests.getAllValues().get(0);
    Assertions.assertInstanceOf(WriteCollections.class, firstWriteRequest);
    List<WriteOp> writeOps = ((WriteCollections) firstWriteRequest).queries;

    // And: All writing collection operations were INSERT for Admin Collections
    Assertions.assertTrue(writeOps.stream().map(writeOp -> writeOp.op).allMatch(op -> op == INSERT));
    Set<String> insertedStoragesIds = writeOps.stream()
        .map(writeOp -> ((StorageCollection) writeOp.feature).getId())
        .collect(Collectors.toSet());
    Assertions.assertEquals(new HashSet<>(NakshaAdminCollection.ALL), insertedStoragesIds);
  }

  @Test
  void shouldRunMaintenanceToEnsureParitionsAvailability() {
    // Given: Physical admin storage that's capable of creating write sessions
    IWriteSession physicalWriteSession = mock(IWriteSession.class);
    when(physicalAdminStorage.newWriteSession(NAKSHA_CONTEXT, true)).thenReturn(physicalWriteSession);

    // And: all write requests to physical storage are successful
    when(physicalWriteSession.execute(any(WriteRequest.class))).thenReturn(new SuccessResult());

    // When: creating new Admin Storage
    new TestAdminStorage(physicalAdminStorage, NAKSHA_CONTEXT);

    // Then; maintenance is run
    verify(physicalAdminStorage, times(1)).maintainNow();
  }

  @Test
  void shouldInsertDefaultStorage() {
    // Given: Physical admin storage that's capable of creating write sessions
    IWriteSession physicalWriteSession = mock(IWriteSession.class);
    when(physicalAdminStorage.newWriteSession(NAKSHA_CONTEXT, true)).thenReturn(physicalWriteSession);

    // And: all write requests to physical storage are successful
    when(physicalWriteSession.execute(any(WriteRequest.class))).thenReturn(new SuccessResult());

    // When: creating new Admin Storage
    AdminStorage adminStorage = new TestAdminStorage(physicalAdminStorage, NAKSHA_CONTEXT);

    // Then: There was a write request on physical admin storage
    ArgumentCaptor<WriteRequest> adminWriteRequests = ArgumentCaptor.forClass(WriteRequest.class);
    verify(physicalWriteSession, times(2)).execute(adminWriteRequests.capture());

    // And: First write requests was about writing features
    WriteRequest firstWriteRequest = adminWriteRequests.getAllValues().get(1);
    Assertions.assertInstanceOf(WriteFeatures.class, firstWriteRequest);
    List<WriteOp> writeOps = ((WriteFeatures) firstWriteRequest).queries;

    // And: There was one operation and it was about wirting admin storage
    Assertions.assertEquals(1, writeOps.size());
    WriteOp writeOp = writeOps.get(0);
    Assertions.assertInstanceOf(Storage.class, writeOp.feature);
    Storage insertedStorage = (Storage) writeOp.feature;
    Assertions.assertEquals(DEFAULT_STORAGE_TEST_CLASS_NAME, insertedStorage.getClassName());
    Assertions.assertEquals(DEFAULT_STORAGE_TEST_ID, insertedStorage.getId());
  }

  static class TestAdminStorage extends AdminStorage {

    static final String DEFAULT_STORAGE_TEST_CLASS_NAME = "TestAdminStorage";
    static final String DEFAULT_STORAGE_TEST_ID = "test_admin_storage_id";

    protected TestAdminStorage(IStorage physicalAdminStorage, NakshaContext nakshaContext) {
      super(physicalAdminStorage, nakshaContext);
    }

    @Override
    protected Storage fetchDefaultStorage() {
      return new Storage(DEFAULT_STORAGE_TEST_CLASS_NAME, DEFAULT_STORAGE_TEST_ID);
    }
  }
}
