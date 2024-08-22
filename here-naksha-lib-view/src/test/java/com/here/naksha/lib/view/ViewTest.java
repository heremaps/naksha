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
package com.here.naksha.lib.view;

import com.here.naksha.lib.core.*;
import com.here.naksha.lib.core.exceptions.TooManyTasks;
import com.here.naksha.lib.core.exceptions.UncheckedException;
import naksha.model.*;
import com.here.naksha.lib.core.util.storage.RequestHelper;
import com.here.naksha.lib.view.concurrent.LayerReadRequest;
import com.here.naksha.lib.view.concurrent.ParallelQueryExecutor;
import com.here.naksha.lib.view.merge.MergeByStoragePriority;
import com.here.naksha.lib.view.missing.IgnoreMissingResolver;
import naksha.model.objects.NakshaFeature;
import naksha.model.request.*;
import naksha.model.request.query.AnyOp;
import naksha.model.request.query.POr;
import naksha.model.request.query.PQuery;
import naksha.model.request.query.Property;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.*;
import java.util.concurrent.TimeoutException;

import static com.here.naksha.lib.view.Sample.sampleXyzResponse;
import static com.here.naksha.lib.view.Sample.sampleXyzWriteResponse;
import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ViewTest {

  private NakshaContext nc = NakshaContext.currentContext().withAppId("VIEW_API_TEST").withAuthor("VIEW_API_AUTHOR");

  private SessionOptions sessionOptions = new SessionOptions();

  private final Write write = new Write();

  private final static String TOPO = "topologies";

  @Test
  void testReadApiNotation() {

    // given
    IStorage storage = mock(IStorage.class);
    ViewLayer topologiesDS = new ViewLayer(storage, "topologies");
    ViewLayer buildingsDS = new ViewLayer(storage, "buildings");
    ViewLayer topologiesCS = new ViewLayer(storage, "topologies");

    // each layer is going to return 3 same records
    List<ResultTuple> results = sampleXyzResponse(3, storage);
    when(storage.newReadSession(sessionOptions)).thenReturn(new MockReadSession(results));

    ViewLayerCollection viewLayerCollection = new ViewLayerCollection("myCollection", topologiesDS, buildingsDS, topologiesCS);

    View view = new View(viewLayerCollection);

    MergeOperation customMergeOperation = new MergeByStoragePriority();
    MissingIdResolver skipFetchingResolver = new IgnoreMissingResolver();

    // when
    ViewReadSession readSession = view.newReadSession(sessionOptions);
    ReadFeatures readFeatures = new ReadFeatures();
    readFeatures.setQueryHistory(true);
    Response result = readSession.execute(
        readFeatures, customMergeOperation, skipFetchingResolver);
    assertInstanceOf(SuccessResponse.class,result);

    // then
    List<ResultTuple> allFeatures = ((SuccessResponse) result).getTuples();
    assertEquals(3, allFeatures.size());
    assertTrue(allFeatures.containsAll(results));
  }

  @Test
  void testWriteApiNotation() {
    final String VIEW_COLLECTION = "myCollection";
    IStorage storage = mock(IStorage.class);
    IMap map = mock(IMap.class);
    IWriteSession session = mock(IWriteSession.class);

    ViewLayer topologiesDS = new ViewLayer(storage, "topologies");
    ViewLayerCollection viewLayerCollection = new ViewLayerCollection(VIEW_COLLECTION, topologiesDS);
    View view = new View(viewLayerCollection);
    when(storage.newWriteSession(sessionOptions)).thenReturn(session);
    when(storage.getMapId(any(Integer.class))).thenReturn(VIEW_COLLECTION);
    when(storage.getId()).thenReturn("Mock Storage");
    when(storage.get(any())).thenReturn(map);
    when(map.getCollectionId(any())).thenReturn("Mock Collection");

    final LayerWriteFeatureRequest request = new LayerWriteFeatureRequest();
    final NakshaFeature feature = new NakshaFeature("id0");
    request.add(write.createFeature(null,VIEW_COLLECTION,feature));
    when(storage.rowToFeature(any())).thenReturn(feature);

    Response success = new SuccessResponse(sampleXyzWriteResponse(1, storage, ExecutedOp.CREATED));
    when(session.execute(request)).thenReturn(success);
    ViewWriteSession writeSession = view.newWriteSession(sessionOptions);
    Response response = writeSession.execute(request);
    assertInstanceOf(SuccessResponse.class,response);
    SuccessResponse successResponse = (SuccessResponse) response;
    assertEquals(feature.getId(), successResponse.getFeatures().get(0).getId());
    assertEquals(ExecutedOp.CREATED, successResponse.getTuples().get(0).op);
    writeSession.commit();
  }

  @Test
  void testDeleteApiNotation() {
    final String VIEW_COLLECTION = "myCollection";
    IStorage storage = mock(IStorage.class);
    IWriteSession session = mock(IWriteSession.class);

    ViewLayer topologiesDS = new ViewLayer(storage, "topologies");
    ViewLayerCollection viewLayerCollection = new ViewLayerCollection(VIEW_COLLECTION, topologiesDS);
    View view = new View(viewLayerCollection);
    when(storage.newWriteSession(sessionOptions)).thenReturn(session);

    final LayerWriteFeatureRequest request = new LayerWriteFeatureRequest();
    final NakshaFeature feature = new NakshaFeature("id0");
    request.add(write.deleteFeature(null,VIEW_COLLECTION,feature,false));
    SuccessResponse successResponse1 = new SuccessResponse(sampleXyzWriteResponse(1, storage, ExecutedOp.DELETED));
    when(session.execute(request)).thenReturn(successResponse1);
    ViewWriteSession writeSession = view.newWriteSession(sessionOptions);

    Response response = writeSession.execute(request);
    assertInstanceOf(SuccessResponse.class,response);
    SuccessResponse successResponse = (SuccessResponse) response;
    assertEquals(feature.getId(), successResponse.getTuples().get(0).featureId);
    assertEquals(ExecutedOp.DELETED, successResponse.getTuples().get(0).op);
    writeSession.commit();
  }

  @Test
  void testExceptionInOneOfTheThreads() {
    // given
    IReadSession readSession = mock(IReadSession.class);
    when(readSession.execute(any())).thenThrow(RuntimeException.class);

    IStorage topologiesStorage = mock(IStorage.class);
    IStorage buildingsStorage = mock(IStorage.class);
    ViewLayer topologiesDS = new ViewLayer(topologiesStorage, "topologies");
    ViewLayer buildingsDS = new ViewLayer(buildingsStorage, "buildings");

    List<ResultTuple> results = sampleXyzResponse(3,topologiesStorage);
    when(topologiesStorage.newReadSession(sessionOptions)).thenReturn(new MockReadSession(results));
    when(buildingsStorage.newReadSession(sessionOptions)).thenReturn(readSession);

    ViewLayerCollection viewLayerCollection = new ViewLayerCollection("myCollection", topologiesDS, buildingsDS);
    View view = new View(viewLayerCollection);

    // expect
    assertThrows(UncheckedException.class, () -> view.newReadSession(sessionOptions).execute(new ReadFeatures()));
  }

  @Test
  void shouldNotQueryForMissingIfOriginalRequestWasOnlyById() {
    // given
    IStorage topologiesStorage_1 = mock(IStorage.class);
    IStorage topologiesStorage_2 = mock(IStorage.class);
    IReadSession readSession = mock(IReadSession.class);
    when(readSession.execute(any())).thenReturn(new SuccessResponse(emptyList()));

    ViewLayer topologiesDS_1 = new ViewLayer(topologiesStorage_1, TOPO);
    ViewLayer topologiesDS_2 = new ViewLayer(topologiesStorage_2, TOPO);

    List<ResultTuple> results = sampleXyzResponse(3, topologiesStorage_2);
    when(topologiesStorage_1.newReadSession(sessionOptions)).thenReturn(readSession);
    when(topologiesStorage_2.newReadSession(sessionOptions)).thenReturn(new MockReadSession(results));

    ViewLayerCollection viewLayerCollection = new ViewLayerCollection("myCollection", topologiesDS_1, topologiesDS_2);
    View view = new View(viewLayerCollection);

    // when only by id
    ReadFeatures request1 = RequestHelper.readFeaturesByIdsRequest(TOPO, List.of("1"));
    SuccessResponse response = (SuccessResponse) view.newReadSession(sessionOptions).execute(request1);
    assertNotNull(response.getFeatures());
    // then
    verify(readSession, times(1)).execute(any());

    // when not only by id
    clearInvocations(readSession);
    ReadFeatures request2 = new ReadFeatures();
    POr propQuery = new POr(new PQuery(new Property(Property.ID), AnyOp.IS_ANY_OF, new int[]{1}), new PQuery(new Property(Property.APP_ID), AnyOp.IS_ANY_OF, new String[]{"app"}));
    RequestQuery requestQuery = new RequestQuery();
    requestQuery.setProperties(propQuery);
    request2.setQuery(requestQuery);
    SuccessResponse response2 = (SuccessResponse) view.newReadSession(sessionOptions).execute(request2);
    assertNotNull(response2.getFeatures());
    verify(readSession, times(2)).execute(any());
  }

  @Test
  void testTimeoutExceptionInOneOfTheThreads() {
    IStorage topologiesStorage = mock(IStorage.class);
    IStorage buildingsStorage = mock(IStorage.class);
    ViewLayer topologiesDS = new ViewLayer(topologiesStorage, "topologies");
    ViewLayer buildingsDS = new ViewLayer(buildingsStorage, "buildings");

    // given
    IReadSession topoReadSession = mock(IReadSession.class);
    IReadSession buildReadSession = mock(IReadSession.class);

    when(topoReadSession.execute(any())).thenThrow(new RuntimeException(new TimeoutException()));
    SuccessResponse successResponse = new SuccessResponse(sampleXyzResponse(1, buildingsStorage));
    when(buildReadSession.execute(any())).thenReturn(successResponse);

    when(topologiesStorage.newReadSession(sessionOptions)).thenReturn(topoReadSession);
    when(buildingsStorage.newReadSession(sessionOptions)).thenReturn(buildReadSession);

    ViewLayerCollection viewLayerCollection = new ViewLayerCollection("myCollection", buildingsDS, topologiesDS);
    View view = new View(viewLayerCollection);
    final ReadFeatures readFeatures = new ReadFeatures();
    readFeatures.setQueryHistory(true);
    Throwable exception = assertThrows(UncheckedException.class, () -> view.newReadSession(sessionOptions).execute(readFeatures));
    assertTrue(exception.getMessage().contains("TimeoutException"));
    verify(topoReadSession, times(1)).execute(any());
    verify(buildReadSession, times(1)).execute(any());
  }

  @Test
  void shouldThrowTooManyTasksException() {
    IStorage mockStorage = mock(IStorage.class);
    IRequestLimitManager requestLimitManager= new DefaultRequestLimitManager(30,100);
    AbstractTask.setConcurrencyLimitManager(requestLimitManager);
    long limit = requestLimitManager.getInstanceLevelLimit();
    ViewLayer[] layerDS = new ViewLayer[(int) (limit + 10)];
    //Create ThreadFactory Limit + 10 layers
    for (int ind = 0; ind < layerDS.length; ind++) {
      layerDS[ind] = new ViewLayer(mockStorage, "collection" + ind);
    }
    ViewLayerCollection viewLayerCollection = new ViewLayerCollection("myCollection", layerDS);
    View view = new View(viewLayerCollection);

    List<SimpleTask> tasks = new ArrayList<>();
    try (MockedConstruction<ParallelQueryExecutor> queryExecutor = mockConstruction(ParallelQueryExecutor.class, (mock, context) -> {
      when(mock.queryInParallel(any())).thenAnswer(new Answer<Object>() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          List<LayerReadRequest> requests = invocation.getArgument(0);
          for (LayerReadRequest layerReadRequest : requests) {
            SimpleTask singleTask = new SimpleTask<>(null,nc);
            tasks.add(singleTask);
            singleTask.start(() -> {
              Thread.sleep(1000);
              return null;
            });
          }
          return Collections.emptyMap();
        }
      });
    })) {

      ViewReadSession viewReadSession = view.newReadSession(sessionOptions);
      Throwable ex = assertThrows(TooManyTasks.class, () -> viewReadSession.execute(new ReadFeatures()));
      assertTrue(ex.getMessage().contains("Maximum number of concurrent tasks"));
    }
    //Interrupt sleeping threads in this group to end.
    Optional<SimpleTask> activeTask = tasks.stream().filter(thread -> thread.getThread() != null).findFirst();
    if (activeTask.isPresent()) {
      ThreadGroup threadGroup = activeTask.get().getThread().getThreadGroup();
      threadGroup.interrupt();
      assertEquals(limit, threadGroup.activeCount());
    }
  }

}
