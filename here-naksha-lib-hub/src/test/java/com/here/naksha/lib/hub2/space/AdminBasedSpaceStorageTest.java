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
package com.here.naksha.lib.hub2.space;

import static com.here.naksha.lib.common.SampleNakshaContext.NAKSHA_CONTEXT;
import static java.util.Collections.emptyList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.here.naksha.lib.core.EventPipeline;
import com.here.naksha.lib.core.IEventHandler;
import com.here.naksha.lib.core.models.storage.Notification;
import com.here.naksha.lib.core.models.storage.ReadCollections;
import com.here.naksha.lib.core.models.storage.ReadFeatures;
import com.here.naksha.lib.core.models.storage.ReadRequest;
import com.here.naksha.lib.core.models.storage.WriteCollections;
import com.here.naksha.lib.core.models.storage.WriteFeatures;
import com.here.naksha.lib.core.models.storage.WriteRequest;
import com.here.naksha.lib.core.storage.IReadSession;
import com.here.naksha.lib.core.storage.IWriteSession;
import com.here.naksha.lib.hub2.EventPipelineFactory;
import com.here.naksha.lib.hub2.admin.AdminStorage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

class AdminBasedSpaceStorageTest {

  private static final String ADMIN_SPACE_ID = "test_admin_space";

  @Mock
  AdminStorage adminStorage;

  @Mock
  EventPipelineFactory eventPipelineFactory;

  @Mock
  IEventHandler eventHandler;

  AdminBasedSpaceStorage spaceStorage;

  @BeforeEach
  void setup() {
    MockitoAnnotations.openMocks(this);
    spaceStorage = new AdminBasedSpaceStorage(adminStorage);
    spaceStorage.setVirtualSpaces(Map.of(ADMIN_SPACE_ID, List.of(eventHandler)));
    spaceStorage.setEventPipelineFactory(eventPipelineFactory);
  }

  @Nested
  class ReadingTest {

    @Test
    void shouldFailWhenCreatingReadSessionWithoutVirtualSpaces() {
      // When: virtual spaces were not initialized
      spaceStorage.setVirtualSpaces(null);

      // Then: opening new read session fails
      assertThrows(IllegalStateException.class, () -> {
        spaceStorage.newReadSession(NAKSHA_CONTEXT, true);
      });
    }

    @Test
    void shouldFailWhenCreatingReadSessionWithoutEventPipelineFactory() {
      // When: virtual spaces were not initialized
      spaceStorage.setEventPipelineFactory(null);

      // Then: opening new read session fails
      assertThrows(IllegalStateException.class, () -> {
        spaceStorage.newReadSession(NAKSHA_CONTEXT, true);
      });
    }

    @Test
    void shouldFailWhenExecutingUnknownReadRequest() {
      // Given: new read session
      IReadSession readSession = spaceStorage.newReadSession(NAKSHA_CONTEXT, true);

      // And: read request of unknown type
      ReadRequest rr = new TestReadRequest();

      // Then: executing read request fails
      assertThrows(UnsupportedOperationException.class, () -> readSession.execute(rr));
    }

    @Test
    void shouldDelegateToAdminWhenReadingCollections() {
      // Given: new read session
      IReadSession readSession = spaceStorage.newReadSession(NAKSHA_CONTEXT, true);

      // And: admin that is able to create reading session
      IReadSession adminReadSession = mock(IReadSession.class);
      when(adminStorage.newReadSession(NAKSHA_CONTEXT, true)).thenReturn(adminReadSession);

      // And: read collections request
      ReadCollections readCollections = new ReadCollections().withIds("some collection");

      // When: executin request
      readSession.execute(readCollections);

      // Then: admin read session is used to handle read collection request
      verify(adminReadSession, times(1)).execute(readCollections);
    }

    @Test
    void shouldFailWhenReadingFeaturesFromManyCollections() {
      // Given: new read session
      IReadSession readSession = spaceStorage.newReadSession(NAKSHA_CONTEXT, true);

      // And: read collections request
      ReadFeatures readFeaturesFromManyCollections = new ReadFeatures("c1", "c2");

      // Then: executin request
      assertThrows(
          UnsupportedOperationException.class, () -> readSession.execute(readFeaturesFromManyCollections));
    }

    @Test
    void shouldTriggerEventPipelineWhenReadingFromAdminSpace() {
      // Given: new read session
      IReadSession readSession = spaceStorage.newReadSession(NAKSHA_CONTEXT, true);

      // And: working event pipeline factory
      EventPipeline eventPipeline = mock(EventPipeline.class);
      when(eventPipelineFactory.eventPipeline()).thenReturn(eventPipeline);

      // And: read collections request
      ReadFeatures readFeaturesFromAdmin = new ReadFeatures(ADMIN_SPACE_ID);

      // When: executing request
      readSession.execute(readFeaturesFromAdmin);

      // Then: event pipeline gets triggered to add event handler for admin space
      verify(eventPipeline, times(1)).addEventHandler(eventHandler);
    }

    @Test
    void shouldFailWhenReadingFromCustomSpace() {
      // Given: new read session
      IReadSession readSession = spaceStorage.newReadSession(NAKSHA_CONTEXT, true);

      // And: read collections request
      ReadFeatures readFeaturesFromUnknownSpace = new ReadFeatures("unknown space");

      // Then: operation fails
      assertThrows(UnsupportedOperationException.class, () -> readSession.execute(readFeaturesFromUnknownSpace));
    }

    @Test
    void shouldNotSupportProcessingNotifications() {
      // Given: new read session
      IReadSession readSession = spaceStorage.newReadSession(NAKSHA_CONTEXT, true);

      // Then: processing tets notification fails
      assertThrows(UnsupportedOperationException.class, () -> readSession.process(new TestNotification()));
    }
  }

  @Nested
  class WritingTest {

    @Test
    void shouldFailWhenCreatingWriteSessionWithoutVirtualSpaces() {
      // When: virtual spaces were not initialized
      spaceStorage.setVirtualSpaces(null);

      // Then: opening new read session fails
      assertThrows(IllegalStateException.class, () -> {
        spaceStorage.newWriteSession(NAKSHA_CONTEXT, true);
      });
    }

    @Test
    void shouldFailWhenCreatingWriteSessionWithoutEventPipelineFactory() {
      // When: virtual spaces were not initialized
      spaceStorage.setEventPipelineFactory(null);

      // Then: opening new read session fails
      assertThrows(IllegalStateException.class, () -> {
        spaceStorage.newWriteSession(NAKSHA_CONTEXT, true);
      });
    }

    @Test
    void shouldFailWhenExecutingUnknownWriteRequest() {
      // Given: new write session
      IWriteSession writeSession = spaceStorage.newWriteSession(NAKSHA_CONTEXT, true);

      // And: write request of unknown type
      WriteRequest wr = new TestWriteRequest();

      // Then: executing write request fails
      assertThrows(UnsupportedOperationException.class, () -> writeSession.execute(wr));
    }

    @Test
    void shouldDelegateToAdminWhenWritingCollections() {
      // Given: new write session
      IWriteSession writeSession = spaceStorage.newWriteSession(NAKSHA_CONTEXT, true);

      // And: admin storage that is able to create new write sessions
      IWriteSession adminWriteSession = Mockito.mock(IWriteSession.class);
      when(adminStorage.newWriteSession(NAKSHA_CONTEXT, true)).thenReturn(adminWriteSession);

      // And: write collection request
      WriteCollections writeCollections = new WriteCollections(List.of("some collection"));

      // When: executing request
      writeSession.execute(writeCollections);

      // Then: admin write session is used for execution
      verify(adminWriteSession, times(1)).execute(writeCollections);
    }

    @Test
    void shouldTriggerEventPipelineWhenWritingToAdminSpaces() {
      // Given: new write session
      IWriteSession writeSession = spaceStorage.newWriteSession(NAKSHA_CONTEXT, true);

      // And: working event pipeline factory
      EventPipeline eventPipeline = mock(EventPipeline.class);
      when(eventPipelineFactory.eventPipeline()).thenReturn(eventPipeline);

      // And: write features request
      WriteFeatures writeFeatures = new WriteFeatures(ADMIN_SPACE_ID);

      // When: executing request
      writeSession.execute(writeFeatures);

      // Then: event pipeline is used to add corresponding event handler
      verify(eventPipeline, times(1)).addEventHandler(eventHandler);
    }

    @Test
    void shouldFailWhenWritingToCustomSpaces() {
      // Given: new write session
      IWriteSession writeSession = spaceStorage.newWriteSession(NAKSHA_CONTEXT, true);

      // And: write features request
      WriteFeatures writeFeatures = new WriteFeatures("unknown space");

      // Then: executing request fails
      assertThrows(UnsupportedOperationException.class, () -> writeSession.execute(writeFeatures));
    }

    @Test
    void shouldNotSupportLockingFeatures() {
      // Given: new write session
      IWriteSession writeSession = spaceStorage.newWriteSession(NAKSHA_CONTEXT, true);

      // Then: locking should fail due to lack of support
      assertThrows(
          UnsupportedOperationException.class,
          () -> writeSession.lockFeature("collection", "feature", 100, MILLISECONDS));
    }

    @Test
    void shouldNotSupportLockingStorages() {
      // Given: new write session
      IWriteSession writeSession = spaceStorage.newWriteSession(NAKSHA_CONTEXT, true);

      // Then: locking should fail due to lack of support
      assertThrows(
          UnsupportedOperationException.class, () -> writeSession.lockStorage("lock_id", 100, MILLISECONDS));
    }
  }

  class TestReadRequest extends ReadRequest {}

  class TestWriteRequest extends WriteRequest {

    TestWriteRequest() {
      super(emptyList());
    }
  }

  class TestNotification extends Notification {}
}
