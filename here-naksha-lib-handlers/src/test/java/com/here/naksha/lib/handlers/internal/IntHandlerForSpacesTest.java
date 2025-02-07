package com.here.naksha.lib.handlers.internal;

import static com.here.naksha.lib.handlers.NakshaAdminCollection.EVENT_HANDLERS;
import static com.here.naksha.lib.handlers.NakshaAdminCollection.SPACES;
import static java.util.Collections.emptyList;
import static naksha.model.NakshaError.NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Named.named;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.here.naksha.lib.core.IEvent;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.models.naksha.Space;
import java.util.List;
import java.util.stream.Stream;
import naksha.base.JvmInt64;
import naksha.model.IReadSession;
import naksha.model.IStorage;
import naksha.model.IWriteSession;
import naksha.model.Metadata;
import naksha.model.SessionOptions;
import naksha.model.Tuple;
import naksha.model.TupleNumber;
import naksha.model.Version;
import naksha.model.request.ErrorResponse;
import naksha.model.request.FeatureTuple;
import naksha.model.request.ReadFeatures;
import naksha.model.request.Request;
import naksha.model.request.Response;
import naksha.model.request.SuccessResponse;
import naksha.model.request.Write;
import naksha.model.request.WriteRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class IntHandlerForSpacesTest {

  @Mock
  INaksha naksha;

  IntHandlerForSpaces handler;

  @BeforeEach
  void setup() {
    MockitoAnnotations.openMocks(this);
    handler = new IntHandlerForSpaces(naksha);
  }

  @Test
  void shouldAlwaysAllowDeletion() {
    // Given:
    final Request writeRequest = new WriteRequest().add(new Write().deleteFeatureById(SPACES, "to_delete", null));
    IEvent event = eventWith(writeRequest);

    // And:
    writingToAdminSucceeds();

    // When
    Response result = handler.process(event);

    // Then
    assertInstanceOf(SuccessResponse.class, result);
  }

  @ParameterizedTest
  @MethodSource("persistingWritesWithInvalidSpace")
  void shouldNotStoreSpaceThatViolatesBasicValidation(WriteRequest writeSpace) {
    // Given:
    IEvent event = eventWith(writeSpace);

    // And:
    writingToAdminSucceeds();

    // When
    Response result = handler.process(event);

    // Then
    assertInstanceOf(ErrorResponse.class, result);
  }

  @ParameterizedTest
  @MethodSource("persistingSpaceWithoutValidHandlers")
  void shouldNotStoreSpaceWithMissingHandlers(WriteRequest writeSpace) {
    // Given:
    Space space = (Space) writeSpace.getWrites().get(0).getFeature();
    IEvent event = eventWith(writeSpace);

    // And:
    List<String> existingHandlers = List.of("handler_1", "handler_2");
    List<String> missingHandlerIds = space.getEventHandlerIds().stream()
        .filter(id -> !existingHandlers.contains(id))
        .toList();

    // And
    handlersExist(existingHandlers);
    writingToAdminSucceeds();

    // When
    Response result = handler.process(event);

    // Then
    assertInstanceOf(ErrorResponse.class, result);
    ErrorResponse errorResult = (ErrorResponse) result;
    assertEquals(NOT_FOUND, errorResult.getError().getCode());
    assertEquals(errorResult.getError().getMsg(), "Following handlers defined for Space %s don't exist: %s".formatted(
        space.getId(),
        String.join(",", missingHandlerIds)
    ));
  }

  private static Stream<Named<WriteRequest>> persistingWritesWithInvalidSpace() {
    Space spaceWithoutTitle = space("no_title", null, "some_desc");
    Space spaceWithoutDescription = space("no_desc", "some_title", null);
    return Stream.of(
        named("PUT Space without title", new WriteRequest().add(new Write().upsertFeature(SPACES, spaceWithoutTitle, true))),
        named("UPDATE Space without title", new WriteRequest().add(new Write().updateFeature(SPACES, spaceWithoutTitle, false))),
        named("CREATE Space without title", new WriteRequest().add(new Write().createFeature(SPACES, spaceWithoutTitle))),
        named("PUT Space without description", new WriteRequest().add(new Write().upsertFeature(SPACES, spaceWithoutDescription, true))),
        named("UPDATE Space without description",
            new WriteRequest().add(new Write().updateFeature(SPACES, spaceWithoutDescription, false))),
        named("CREATE Space without description", new WriteRequest().add(new Write().createFeature(SPACES, spaceWithoutDescription)))
    );
  }

  private static Stream<Named<WriteRequest>> persistingSpaceWithoutValidHandlers() {
    Space space = space("space_id", "no_desc", "some_title", List.of("handler_1", "handler_2", "handler_3"));
    return Stream.of(
        named("PUT Space without valid handlers", new WriteRequest().add(new Write().upsertFeature(SPACES, space, true))),
        named("UPDATE Space without valid handlers", new WriteRequest().add(new Write().updateFeature(SPACES, space, false))),
        named("CREATE Space without valid handlers", new WriteRequest().add(new Write().createFeature(SPACES, space)))
    );
  }

  private static Space space(String id, String title, String desc) {
    return space(id, title, desc, emptyList());
  }

  private static Space space(String id, String title, String desc, List<String> handlersIds) {
    Space space = new Space();
    space.setId(id);
    space.setTitle(title);
    space.setDescription(desc);
    for (String handlerId : handlersIds) {
      space.addHandler(handlerId);
    }
    return space;
  }

  private IEvent eventWith(Request request) {
    IEvent event = mock(IEvent.class);
    when(event.getRequest()).thenReturn(request);
    return event;
  }

  private void writingToAdminSucceeds() {
    IStorage admin = mock(IStorage.class);
    when(naksha.getAdminStorage()).thenReturn(admin);
    IWriteSession writeSession = mock(IWriteSession.class);
    when(writeSession.execute(any(WriteRequest.class))).thenReturn(new SuccessResponse());
    when(admin.newWriteSession(any(SessionOptions.class))).thenReturn(writeSession);
  }

  private void handlersExist(List<String> eventHandlerIds) {
    IStorage spaceStorage = mock(IStorage.class);
    when(naksha.getSpaceStorage()).thenReturn(spaceStorage);
    IReadSession readSession = mock(IReadSession.class);
    SuccessResponse successResponse = new TestSuccessResponse(spaceStorage, eventHandlerIds);
    when(readSession.execute(argThat(anyReadHandlersRequest()))).thenReturn(successResponse);
    when(spaceStorage.newReadSession(any(SessionOptions.class))).thenReturn(readSession);
  }

  private ArgumentMatcher<ReadFeatures> anyReadHandlersRequest() {
    return argument -> argument.getCollectionIds().size() == 1 && EVENT_HANDLERS.equals(argument.getCollectionIds().get(0));
  }

  static class TestSuccessResponse extends SuccessResponse {

    TestSuccessResponse(IStorage storage, List<String> ids) {
      super(resultTuples(storage, ids));
    }

    private static List<FeatureTuple> resultTuples(IStorage storage, List<String> ids) {
      return ids.stream()
          .map(id -> {
            TupleNumber tupleNumber = testTupleNumber();
            return new FeatureTuple(tupleNumber, testTuple(tupleNumber, id));
          })
          .toList();
    }

    private static TupleNumber testTupleNumber() {
      return new TupleNumber(new JvmInt64(0L), 0, 0, 0, new Version(0L), 0);
    }

    private static Tuple testTuple(TupleNumber tupleNumber, String id) {
      return new Tuple(testMetadata(tupleNumber, id), null, null, null, null, null, false);
    }

    private static Metadata testMetadata(TupleNumber tupleNumber, String id) {
      return new Metadata(
          tupleNumber,
          0,
          null,
          new JvmInt64(0),
          null,
          null,
          null,
          null,
          1,
          0,
          0,
          id,
          "sampleAppId",
          "sampleAuthor",
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null
      );
    }
  }
}