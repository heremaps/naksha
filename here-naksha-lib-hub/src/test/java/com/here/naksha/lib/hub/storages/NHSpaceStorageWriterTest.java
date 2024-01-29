package com.here.naksha.lib.hub.storages;

import static com.here.naksha.lib.common.assertions.WriteXyzCollectionsAssertions.assertThatWriteXyzCollections;
import static com.here.naksha.lib.common.assertions.WriteXyzFeaturesAssertions.assertThatWriteXyzFeatures;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.here.naksha.lib.common.TestNakshaContext;
import com.here.naksha.lib.core.EventPipeline;
import com.here.naksha.lib.core.IEventHandler;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.NakshaAdminCollection;
import com.here.naksha.lib.core.NakshaContext;
import com.here.naksha.lib.core.models.naksha.Space;
import com.here.naksha.lib.core.models.storage.EWriteOp;
import com.here.naksha.lib.core.models.storage.SuccessResult;
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
import org.mockito.MockitoAnnotations;

class NHSpaceStorageWriterTest {

  private static final String CUSTOM_SPACE = "customSpace";

  @Mock
  INaksha naksha;

  @Mock
  EventPipelineFactory eventPipelineFactory;

  private NHSpaceStorageWriter writer;
  private NakshaContext nakshaContext;

  @BeforeEach
  void setup() {
    MockitoAnnotations.openMocks(this);
    nakshaContext = TestNakshaContext.newTestNakshaContext();
    writer = new NHSpaceStorageWriter(
        naksha,
        Map.of(
            NakshaAdminCollection.SPACES, List.of(mock(IEventHandler.class)),
            CUSTOM_SPACE, List.of(mock(IEventHandler.class))
        ),
        eventPipelineFactory,
        nakshaContext,
        false
    );
  }

  @Test
  void shouldInvokeTwoPipelinesOnDeleteSpaceRequest() {
    // Given: Alwyas succeeding Admin Write session - used for verifying that space (content) deletion was invoked
    IWriteSession adminWriteSession = alwaysSucceedingAdminWriteSession();

    // And: Configured event pipeline spy - used for verifying that space entry deletion was invoked
    EventPipeline eventPipeline = configuredPipelineSpy();

    // And: delete space request
    WriteXyzFeatures deleteSpaceRequest = new WriteXyzFeatures(NakshaAdminCollection.SPACES).delete(new Space(CUSTOM_SPACE));

    // When: executing delete space request
    writer.execute(deleteSpaceRequest);

    // Then: The space itself (contents) got deleted: Admin session executed WriteCollection request with space deletion
    ArgumentCaptor<WriteXyzCollections> writeCollectionsPassedToWriter = ArgumentCaptor.forClass(WriteXyzCollections.class);
    verify(adminWriteSession).execute(writeCollectionsPassedToWriter.capture());
    assertThatWriteXyzCollections(writeCollectionsPassedToWriter.getValue())
        .hasSingleCodecThat(collectionCodec -> collectionCodec
            .hasWriteOp(EWriteOp.DELETE)
            .hasCollectionWithId(CUSTOM_SPACE)
        );

    // And: Space entry in admin collection got deleted: Event Pipeline handled WriteFeature request for space entry deletion
    ArgumentCaptor<WriteRequest> writeRequestPassedToPipeline = ArgumentCaptor.forClass(WriteRequest.class);
    verify(eventPipeline).sendEvent(writeRequestPassedToPipeline.capture());
    assertThatWriteXyzFeatures(writeRequestPassedToPipeline.getValue())
        .hasSingleCodecThat(featureCodec -> featureCodec
            .hasWriteOp(EWriteOp.DELETE)
            .hasFeatureWithId(CUSTOM_SPACE)
        );
  }

  private IWriteSession alwaysSucceedingAdminWriteSession() {
    // Given: Write Session that always succeeds
    IWriteSession writeAdminSession = mock(IWriteSession.class);
    when(writeAdminSession.execute(any(WriteRequest.class))).thenReturn(new SuccessResult());

    // And: Admin storage that delegates to configured Write Session
    IStorage adminStorage = mock(IStorage.class);
    when(naksha.getAdminStorage()).thenReturn(adminStorage);
    when(adminStorage.newWriteSession(nakshaContext, false)).thenReturn(writeAdminSession);
    return writeAdminSession;
  }

  private EventPipeline configuredPipelineSpy() {
    EventPipeline eventPipeline = spy(new EventPipeline(naksha));
    when(eventPipelineFactory.eventPipeline()).thenReturn(eventPipeline);
    return eventPipeline;
  }
}