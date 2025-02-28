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
package com.here.naksha.lib.hub;

import static com.here.naksha.lib.common.TestFileLoader.parseJsonFileOrFail;
import static com.here.naksha.lib.common.TestNakshaContext.newTestNakshaContext;
import static com.here.naksha.lib.core.HubInternalIdentifiers.EVENT_HANDLERS;
import static com.here.naksha.lib.core.HubInternalIdentifiers.SPACES;
import static com.here.naksha.lib.core.HubInternalIdentifiers.STORAGES;
import static com.here.naksha.lib.hub.mock.MockResult.mockResultWithFeature;
import static naksha.model.util.RequestHelper.createFeatureRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.here.naksha.lib.core.EndPipelineHandler;
import com.here.naksha.lib.core.EventPipeline;
import com.here.naksha.lib.core.IEventHandler;
import com.here.naksha.lib.core.models.naksha.EventHandlerConfig;
import com.here.naksha.lib.core.models.naksha.Space;
import com.here.naksha.lib.handlers.AuthorizationEventHandler;
import com.here.naksha.lib.handlers.DefaultStorageHandler;
import com.here.naksha.lib.handlers.internal.IntHandlerForStorageConfigs;
import com.here.naksha.lib.hub.storages.NHAdminStorage;
import com.here.naksha.lib.hub.storages.NHAdminStorageReader;
import com.here.naksha.lib.hub.storages.NHAdminStorageWriter;
import com.here.naksha.lib.hub.storages.NHSpaceStorage;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import naksha.model.IStorage;
import naksha.model.IWriteSession;
import naksha.model.Naksha;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaStorage;
import naksha.model.objects.NakshaFeature;
import naksha.model.request.ReadFeatures;
import naksha.model.request.ReadRequest;
import naksha.model.request.WriteRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NakshaHubWiringTest {

  @Mock
  static NakshaHub hub;

  @Mock
  static NHAdminStorage adminStorage;

  @Mock
  static NHAdminStorageReader adminStorageReader;

  @Mock
  static NHAdminStorageWriter adminStorageWriter;

  static NakshaEventPipelineFactory spyPipelineFactory;

  static NHSpaceStorage spaceStorage;

  @BeforeEach
  void beforeEachTest() {
    MockitoAnnotations.openMocks(this);
    spyPipelineFactory = spy(new NakshaEventPipelineFactory(hub));
    spaceStorage = new NHSpaceStorage(hub, spyPipelineFactory);
    when(hub.getSpaceStorage()).thenReturn(spaceStorage);
    when(hub.getAdminStorage()).thenReturn(adminStorage);
    when(adminStorage.newReadSession(any())).thenReturn(adminStorageReader);
    when(adminStorage.newWriteSession(any())).thenReturn(adminStorageWriter);
    when(adminStorage.useReadSession(any(), any())).thenCallRealMethod();
    when(adminStorage.useWriteSession(any(), any())).thenCallRealMethod();
    doCallRealMethod().when(adminStorage).runInReadSession(any(), any());
    doCallRealMethod().when(adminStorage).runInWriteSession(any(), any());
  }

  @Test
  @Order(1)
  void testCreateStorageRequestWiring() {
    // Given: Create Storage request
    final NakshaStorage storageConfig = parseJsonFileOrFail("create_storage.json", NakshaStorage.class);
    final WriteRequest request = createFeatureRequest(STORAGES, storageConfig);

    // And: spies and captors in place
    final EventPipeline spyPipeline = spy(spyPipelineFactory.eventPipeline());
    when(spyPipelineFactory.eventPipeline()).thenReturn(spyPipeline);
    final ArgumentCaptor<WriteRequest> reqCaptor = ArgumentCaptor.forClass(WriteRequest.class);
    final ArgumentCaptor<IEventHandler> handlerCaptor = ArgumentCaptor.forClass(IEventHandler.class);

    // When: Request is submitted to Hub Space Storage
    hub.getSpaceStorage().runInWriteSession(SessionOptions.from(newTestNakshaContext(), true), admin -> {
      admin.execute(request);
      admin.commit();
    });

    // Then:
    // Verify: 2 event pipelines created (1 actual + 1 due to spy setup)
    verify(spyPipelineFactory, times(2)).eventPipeline();
    // Verify: 3 known event handlers are added to the pipeline
    verify(spyPipeline, times(3)).addEventHandler(handlerCaptor.capture());
    final List<IEventHandler> handlers = handlerCaptor.getAllValues();
    assertTrue(
        handlers.get(0) instanceof AuthorizationEventHandler, "Expected instance of AuthorizationEventHandler");
    assertTrue(handlers.get(1) instanceof IntHandlerForStorageConfigs, "Expected instance of IntHandlerForStorages");
    assertTrue(handlers.get(2) instanceof EndPipelineHandler, "Expected instance of EndPipelineHandler");

    // Verify: admin storage writer finally gets the write request
    verify(adminStorageWriter, times(1)).execute(reqCaptor.capture());
    assertTrue(reqCaptor.getValue() instanceof WriteRequest);
  }

  @Test
  @Order(2)
  void testGetStoragesRequestWiring() throws Exception {
    // Given: Read Storage request
    final ReadFeatures request = new ReadFeatures().addCollectionId(STORAGES);

    // And: spies and captors in place
    final EventPipeline spyPipeline = spy(spyPipelineFactory.eventPipeline());
    when(spyPipelineFactory.eventPipeline()).thenReturn(spyPipeline);
    final ArgumentCaptor<ReadRequest> reqCaptor = ArgumentCaptor.forClass(ReadRequest.class);
    final ArgumentCaptor<IEventHandler> handlerCaptor = ArgumentCaptor.forClass(IEventHandler.class);

    // When: Request is submitted to Hub Space Storage
    hub.getSpaceStorage().runInReadSession(SessionOptions.from(newTestNakshaContext(), true), reader -> {
      reader.execute(request);
    });

    // Then:
    // Verify: 2 event pipelines created (1 actual + 1 due to spy setup)
    verify(spyPipelineFactory, times(2)).eventPipeline();
    // Verify: 3 known event handlers are added to the pipeline
    verify(spyPipeline, times(3)).addEventHandler(handlerCaptor.capture());
    final List<IEventHandler> handlers = handlerCaptor.getAllValues();
    assertTrue(
        handlers.get(0) instanceof AuthorizationEventHandler, "Expected instance of AuthorizationEventHandler");
    assertTrue(handlers.get(1) instanceof IntHandlerForStorageConfigs, "Expected instance of IntHandlerForStorages");
    assertTrue(handlers.get(2) instanceof EndPipelineHandler, "Expected instance of EndPipelineHandler");
    // Verify: admin storage writer finally gets the write request
    verify(adminStorageReader, times(1)).execute(reqCaptor.capture());
    assertTrue(reqCaptor.getValue() instanceof ReadFeatures);
  }

  @Test
  @Order(3)
  void testCreateFeatureRequestWiring() throws Exception {
    // Given: Storage, EventHandler and Space objects
    final NakshaStorage storageConfig = parseJsonFileOrFail("createFeature/create_storage.json", NakshaStorage.class);
    final EventHandlerConfig eventHandler =
        parseJsonFileOrFail("createFeature/create_event_handler.json", EventHandlerConfig.class);
    final Space space = parseJsonFileOrFail("createFeature/create_space.json", Space.class);
    final IStorage storageImpl = Naksha.useStorage(storageConfig);

    // And: mock in place to return given Storage, EventHandler and Space objects, when requested from Admin Storage
    final IStorage spyStorageImpl = spy(storageImpl);
    when(adminStorageReader.execute(argThat(readRequest -> {
      if (readRequest instanceof ReadFeatures rr) {
        return Objects.equals(rr.getCollectionIds().get(0), SPACES);
      }
      return false;
    })))
        .thenReturn(mockResultWithFeature(space));
    when(adminStorageReader.execute(argThat(readRequest -> {
      if (readRequest instanceof ReadFeatures rr) {
        return Objects.equals(rr.getCollectionIds().get(0), EVENT_HANDLERS);
      }
      return false;
    })))
        .thenReturn(mockResultWithFeature(eventHandler));
    when(hub.getStorageById(argThat(argument -> argument.equals(storageConfig.getId()))))
        .thenReturn(spyStorageImpl);
    // And: setup spy on Custom Storage Writer to intercept execute() method calls
    final IWriteSession spyWriter = spy(spyStorageImpl.newWriteSession(SessionOptions.from(newTestNakshaContext(), true)));
    doReturn(spyWriter).when(spyStorageImpl).newWriteSession(any());

    // And: Create Feature request
    final NakshaFeature feature = parseJsonFileOrFail("createFeature/create_feature.json", NakshaFeature.class);
    final WriteRequest request = createFeatureRequest(space.getId(), feature);

    // And: spies and captors in place to return
    final EventPipeline spyPipeline = spy(spyPipelineFactory.eventPipeline());
    when(spyPipelineFactory.eventPipeline()).thenReturn(spyPipeline);
    final ArgumentCaptor<WriteRequest> reqCaptor = ArgumentCaptor.forClass(WriteRequest.class);
    final ArgumentCaptor<IEventHandler> handlerCaptor = ArgumentCaptor.forClass(IEventHandler.class);

    // When: Request is submitted to Hub Space Storage
    hub.getSpaceStorage().runInWriteSession(SessionOptions.from(newTestNakshaContext(), true), writer -> {
      writer.execute(request);
      writer.commit();
    });

    // Then:
    // Verify: 2 event pipelines created (1 actual + 1 due to spy setup)
    verify(spyPipelineFactory, times(2)).eventPipeline();
    // Verify: 3 known event handlers are added to the pipeline
    verify(spyPipeline, times(3)).addEventHandler(handlerCaptor.capture());
    final List<IEventHandler> handlers = handlerCaptor.getAllValues();
    assertTrue(
        handlers.get(0) instanceof AuthorizationEventHandler, "Expected instance of AuthorizationEventHandler");
    assertTrue(handlers.get(1) instanceof DefaultStorageHandler, "Expected instance of DefaultStorageHandler");
    assertTrue(handlers.get(2) instanceof EndPipelineHandler, "Expected instance of EndPipelineHandler");
    // Verify: admin storage writer finally gets:
    //    3 write requests (create feature, create missing collection, reattempt create feature)
    //    + 1 for setting up spy
    verify(spyStorageImpl, times(4)).newWriteSession(any());
    verify(spyWriter, times(3)).execute(reqCaptor.capture());
    assertTrue(reqCaptor.getValue() instanceof WriteRequest);
    final List<WriteRequest> requests = reqCaptor.getAllValues();
    final String collectionId = ((Map) space.getProperties().get("collection"))
        .get("id")
        .toString(); // TODO: this is ambiguous (see Space::getCollectionId), discuss
    // Verify: WriteFeature into collection got called
    assertTrue(requests.get(0) instanceof WriteRequest, "Expected WriteRequest type of request.");
    assertEquals(
        collectionId,
        ((WriteRequest) requests.get(0)).getWrites().get(0).getCollectionId(),
        "CollectionId mismatch for Write Feature request");
    // Verify: WriteCollection got called (to create missing table)
    assertTrue(requests.get(1) instanceof WriteRequest, "Expected WriteRequest type of request.");
    assertEquals(
        collectionId,
        requests.get(1)
            .getWrites()
            .get(0)
            .getFeatureId(),
        "CollectionId mismatch for Write Collection request");
    // Verify: WriteFeature into collectionId got called again
    assertTrue(
        requests.get(2) instanceof WriteRequest,
        "Expected WriteXyzFeatures type of request during reattempt.");
    assertEquals(
        collectionId,
        requests.get(2).getWrites().get(0).getCollectionId(),
        "CollectionId mismatch for reattempted Write Feature request");
  }
}
