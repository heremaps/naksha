package com.here.naksha.lib.hub.storages;


import static com.here.naksha.lib.common.assertions.WriteRequestAssertions.assertThatWriteRequest;
import static com.here.naksha.lib.core.HubInternalIdentifiers.SPACES;
import static naksha.model.NakshaContext.currentContext;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.here.naksha.lib.common.TestNakshaContext;
import com.here.naksha.lib.core.EventPipeline;
import com.here.naksha.lib.core.HubInternalIdentifiers;
import com.here.naksha.lib.core.IEventHandler;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.hub.EventPipelineFactory;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import naksha.model.Naksha;
import naksha.model.NakshaContext;
import naksha.model.NakshaError;
import naksha.model.SessionOptions;
import naksha.model.request.ErrorResponse;
import naksha.model.request.Response;
import naksha.model.request.SuccessResponse;
import naksha.model.request.Write;
import naksha.model.request.WriteOp;
import naksha.model.request.WriteRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
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
            SPACES, List.of(mock(IEventHandler.class)),
            CUSTOM_SPACE, List.of(mock(IEventHandler.class))
        ),
        eventPipelineFactory,
        SessionOptions.from(
            nakshaContext,
            false
        )
    );
  }

  @Test
  void shouldInvokeTwoPipelinesOnDeleteSpaceRequest() {
    // Given: Configured event pipeline spy - used for verifying that space entry deletion was invoked
    EventPipeline eventPipeline = alwaysSucceedingPipeline();

    // And: delete space request
    WriteRequest deleteSpaceRequest = new WriteRequest().add(
        new Write().deleteFeatureById(currentContext().getMapId(), SPACES, CUSTOM_SPACE));

    // When: executing delete space request
    Response result = writer.execute(deleteSpaceRequest);

    // Then: Event Pipeline received two Write Requests
    List<WriteRequest> requestsPassedToPipeline = requestsPassedToPipeline(eventPipeline);
    Assertions.assertEquals(2, requestsPassedToPipeline.size());

    // And: the first request was about deleting (purging) underlying collection
    assertThatWriteRequest(requestsPassedToPipeline.get(0))
        .hasSingleWriteThat(write -> write
            .hasOp(WriteOp.DELETE)
            .hasCollectionId(Naksha.COLLECTIONS_COL)
            .hasId(CUSTOM_SPACE)
        );

    // And: the seoncd request was about deleting space entry
    assertThatWriteRequest(requestsPassedToPipeline.get(1))
        .hasSingleWriteThat(write -> write
            .hasOp(WriteOp.DELETE)
            .hasCollectionId(SPACES)
            .hasId(CUSTOM_SPACE)
        );

    // And: Result of the whole operation is positive
    assertInstanceOf(SuccessResponse.class, result);
  }

  @Test
  void shouldNotTriggerSpaceEntryDeletionWhenPurgingFailed() {
    // Given: Configured event pipeline spy that fails on WriteCollections
    EventPipeline eventPipeline = eventPipelineFailingOn(writeCollectionRequest());

    // And: delete space request
    WriteRequest deleteSpaceRequest = new WriteRequest().add(
        new Write().deleteFeatureById(currentContext().getMapId(), SPACES, CUSTOM_SPACE));

    // When: executing delete space request
    Response response = writer.execute(deleteSpaceRequest);

    // Then: Event Pipeline received single Write Request
    List<WriteRequest> requestsPassedToPipeline = requestsPassedToPipeline(eventPipeline);
    Assertions.assertEquals(1, requestsPassedToPipeline.size());

    // And: that request was about deleting (purging) collection
    assertThatWriteRequest(requestsPassedToPipeline.get(0))
        .hasSingleWriteThat(write -> write
            .hasOp(WriteOp.DELETE)
            .hasCollectionId(Naksha.COLLECTIONS_COL)
            .hasId(CUSTOM_SPACE)
        );

    // And: Result of the whole operation is negative, as purging failes
    assertInstanceOf(ErrorResponse.class, response);
  }

  @Test
  void shouldFailWhenSpaceEntryDeletionFailed() {
    // Given: Configured event pipeline spy that fails on writes to SPACES (ie when deleting a space)
    ArgumentMatcher<WriteRequest> anyWriteFeatureToAdminSpaces = writeFeaturesRequest(SPACES);
    EventPipeline eventPipeline = eventPipelineFailingOn(anyWriteFeatureToAdminSpaces);

    // And: delete space request
    WriteRequest deleteSpaceRequest = new WriteRequest().add(
        new Write().deleteFeatureById(currentContext().getMapId(), SPACES, CUSTOM_SPACE));

    // When: executing delete space request
    Response response = writer.execute(deleteSpaceRequest);

    // Then: Event Pipeline received two Write Request
    List<WriteRequest> requestsPassedToPipeline = requestsPassedToPipeline(eventPipeline);
    Assertions.assertEquals(2, requestsPassedToPipeline.size());

    // And: the first request was about deleting (purging) underlying collection
    assertThatWriteRequest(requestsPassedToPipeline.get(0))
        .hasSingleWriteThat(write -> write
            .hasOp(WriteOp.DELETE)
            .hasCollectionId(Naksha.COLLECTIONS_COL)
            .hasId(CUSTOM_SPACE)
        );

    // And: the second request was about deleting space entry
    assertThatWriteRequest(requestsPassedToPipeline.get(1))
        .hasSingleWriteThat(write -> write
            .hasOp(WriteOp.DELETE)
            .hasCollectionId(SPACES)
            .hasId(CUSTOM_SPACE)
        );

    // And: Result of the whole operation is negative (space entry deletion failed)
    assertInstanceOf(ErrorResponse.class, response);
  }

  private List<WriteRequest> requestsPassedToPipeline(EventPipeline eventPipeline) {
    ArgumentCaptor<WriteRequest> requestPassedToPipeline = ArgumentCaptor.forClass(WriteRequest.class);
    verify(eventPipeline, atLeast(1)).sendEvent(requestPassedToPipeline.capture());
    return requestPassedToPipeline.getAllValues();
  }

  private EventPipeline alwaysSucceedingPipeline() {
    return eventPipelineFailingOn(null);
  }

  private EventPipeline eventPipelineFailingOn(ArgumentMatcher<WriteRequest> failingWrite) {
    EventPipeline eventPipeline = spy(new EventPipeline(naksha));
    when(eventPipeline.sendEvent(any())).thenReturn(new SuccessResponse());
    if (failingWrite != null) {
      when(eventPipeline.sendEvent(argThat(failingWrite))).thenReturn(
          new ErrorResponse(new NakshaError(NakshaError.ILLEGAL_ARGUMENT, "Configured to fail")));
    }
    clearInvocations(eventPipeline);
    when(eventPipelineFactory.eventPipeline()).thenReturn(eventPipeline);
    return eventPipeline;
  }

  private ArgumentMatcher<WriteRequest> writeCollectionRequest() {
    return writeRequest -> {
      List<Write> writes = writeRequest.getWrites();
      return writes.size() == 1 && writes.get(0).getCollectionId().equals(Naksha.COLLECTIONS_COL);
    };
  }

  private ArgumentMatcher<WriteRequest> writeFeaturesRequest(String collectionId) {
    return writeRequest -> {
      Set<String> collectionIds = writeRequest.getWrites().stream().map(Write::getCollectionId).collect(Collectors.toSet());
      return collectionIds.size() == 1 && collectionIds.contains(collectionId);
    };
  }
}