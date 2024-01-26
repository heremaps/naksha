package com.here.naksha.lib.hub.storages;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.here.naksha.lib.common.TestNakshaContext;
import com.here.naksha.lib.core.EventPipeline;
import com.here.naksha.lib.core.IEvent;
import com.here.naksha.lib.core.IEventHandler;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.NakshaAdminCollection;
import com.here.naksha.lib.core.NakshaContext;
import com.here.naksha.lib.core.models.naksha.Space;
import com.here.naksha.lib.core.models.payload.Event;
import com.here.naksha.lib.core.models.storage.Request;
import com.here.naksha.lib.core.models.storage.Result;
import com.here.naksha.lib.core.models.storage.SuccessResult;
import com.here.naksha.lib.core.models.storage.WriteCollections;
import com.here.naksha.lib.core.models.storage.WriteRequest;
import com.here.naksha.lib.core.models.storage.WriteXyzCollections;
import com.here.naksha.lib.core.models.storage.WriteXyzFeatures;
import com.here.naksha.lib.core.storage.IStorage;
import com.here.naksha.lib.core.storage.IWriteSession;
import com.here.naksha.lib.hub.EventPipelineFactory;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

class NHSpaceStorageWriterTest {

  private static final String CUSTOM_SPACE = "customSpace";

  @Mock
  INaksha naksha;

  @Mock
  EventPipelineFactory eventPipelineFactory;

  @Mock
  IEventHandler spacesHandler;

  @Mock
  IEventHandler customSpaceHandler;

  private NHSpaceStorageWriter writer;
  private NakshaContext nakshaContext;

  @BeforeEach
  void setup() {
    MockitoAnnotations.openMocks(this);
    nakshaContext = TestNakshaContext.newTestNakshaContext();
    writer = new NHSpaceStorageWriter(
        naksha,
        Map.of(
            NakshaAdminCollection.SPACES, List.of(spacesHandler),
            CUSTOM_SPACE, List.of(customSpaceHandler)
        ),
        eventPipelineFactory,
        nakshaContext,
        false
    );
  }

  @Test
  void shouldInvokeTwoPipelinesOnDeleteSpaceRequest() {
    // Given: Write admin session that succeeds on writes
    IWriteSession writeAdminSession = mock(IWriteSession.class);
    when(writeAdminSession.execute(any(WriteRequest.class))).thenReturn(new SuccessResult());

    when(spacesHandler.processEvent(any())).thenReturn(new SuccessResult());

    // And: Admin storage that delegates write requests to conifgured write session
    IStorage adminStorage = mock(IStorage.class);
    when(adminStorage.newWriteSession(nakshaContext, false)).thenReturn(writeAdminSession);
    when(naksha.getAdminStorage()).thenReturn(adminStorage);

    // And:
    EventPipeline eventPipeline = spy(new EventPipeline(naksha));
    when(eventPipelineFactory.eventPipeline()).thenReturn(eventPipeline);

    // And: delete space request
    WriteXyzFeatures deleteSpaceRequest = new WriteXyzFeatures(NakshaAdminCollection.SPACES).delete(new Space(CUSTOM_SPACE));

    // When:
    writer.execute(deleteSpaceRequest);

    // Then: Admin session executed WriteCollection request with space deletion
    ArgumentCaptor<WriteXyzCollections> writeCollectionsCaptor = ArgumentCaptor.forClass(WriteXyzCollections.class);
    Mockito.verify(writeAdminSession).execute(writeCollectionsCaptor.capture());
    WriteXyzCollections writeXyzCollections = writeCollectionsCaptor.getValue();
    Assertions.assertNotNull(writeXyzCollections);
    Assertions.assertEquals(1, writeXyzCollections.features.size());
    Assertions.assertEquals(CUSTOM_SPACE, writeXyzCollections.features.get(0).getFeature().getId());

    // And:
    ArgumentCaptor<WriteRequest> requestCaptor = ArgumentCaptor.forClass(WriteRequest.class);
    verify(eventPipeline).sendEvent(requestCaptor.capture());
    Object x = requestCaptor.getValue().features.get(0);
    Assertions.assertNotNull(x);
  }

}