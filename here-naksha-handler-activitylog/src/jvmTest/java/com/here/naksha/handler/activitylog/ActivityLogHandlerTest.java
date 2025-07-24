package com.here.naksha.handler.activitylog;

import static com.here.naksha.handler.activitylog.ActivityLogHandlerProperties.activityLogHandlerProperties;
import static com.here.naksha.handler.activitylog.NakshaFeatureBuilder.nakshaFeature;
import static com.here.naksha.handler.activitylog.assertions.ActivityLogSuccessResultAssertions.assertThatResult;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.here.naksha.handler.activitylog.sample.DatahubSamplesUtil;
import com.here.naksha.handler.activitylog.sample.DatahubSamplesUtil.DatahubSample;
import com.here.naksha.lib.core.IEvent;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.models.naksha.EventHandlerConfig;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import naksha.base.JvmInt64;
import naksha.model.Action;
import naksha.model.IReadSession;
import naksha.model.IStorage;
import naksha.model.NakshaContext;
import naksha.model.NakshaError;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaFeatureList;
import naksha.model.request.ErrorResponse;
import naksha.model.request.ReadCollections;
import naksha.model.request.ReadFeatures;
import naksha.model.request.ReadRequest;
import naksha.model.request.Request;
import naksha.model.request.Response;
import naksha.model.request.SuccessResponse;
import naksha.model.request.Write;
import naksha.model.request.WriteRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

class ActivityLogHandlerTest {

  private static final String SPACE_ID = "test_activity_space";
  private static final JvmInt64 T0 = new JvmInt64(1749477141945L);
  private static final JvmInt64 T1 = new JvmInt64(1749477141955L);

  @Mock
  INaksha naksha;

  @Mock
  EventHandlerConfig eventHandler;

  @Mock
  IStorage spaceStorage;

  private ActivityLogHandler handler;

  @BeforeEach
  void setup() {
    MockitoAnnotations.openMocks(this);
    when(naksha.getSpaceStorage()).thenReturn(spaceStorage);
    doCallRealMethod().when(spaceStorage).runInReadSession(any(), any());
    doCallRealMethod().when(spaceStorage).useReadSession(any(), any());
    doCallRealMethod().when(spaceStorage).useWriteSession(any(), any());
    doCallRealMethod().when(spaceStorage).runInWriteSession(any(), any());
    handler = handlerForSpaceId(SPACE_ID);
    NakshaContext.currentContext().withAppId("test-app");
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldFailWhenSpaceIdIsMissing(String missingSpaceId) {
    // Given: handler with empty spaceId (null, empty string)
    ActivityLogHandler handlerWithoutSpace = handlerForSpaceId(missingSpaceId);

    // When: handling some red features request
    Response result = handlerWithoutSpace.process(eventWith(new ReadFeatures()));

    // Then: error that indicates Illegal Argument is returned
    assertInstanceOf(ErrorResponse.class, result);
    assertEquals(NakshaError.ILLEGAL_ARGUMENT, ((ErrorResponse) result).getError().getCode());
  }

  @ParameterizedTest
  @MethodSource("unhandledRequests")
  void shouldFailOnUnhandledRequests(Request unhandledRequest) {
    // Given: event bearing some unhandler request
    IEvent event = eventWith(unhandledRequest);

    // When: handler tries to process such event
    Response result = handler.processEvent(event);

    // Then: Storage was not used at all
    verifyNoInteractions(spaceStorage);

    // And: Error result (NOT_IMPLEMENTED) was returned
    assertInstanceOf(ErrorResponse.class, result);
    assertEquals(NakshaError.NOT_IMPLEMENTED, ((ErrorResponse) result).getError().getCode());
  }

  @Test
  void shouldImmediatelySucceedOnWriteCollection() {
    // Given: event bearing some WriteCollections request
    IEvent event = eventWith(new WriteRequest().add(new Write().deleteCollectionById(null,"some_collection")));

    // When: handler tries to process such event
    Response result = handler.processEvent(event);

    // Then: storage was not used at all
    verifyNoInteractions(spaceStorage);

    // And: event was not sent upstream
    verify(event, never()).sendUpstream();
    verify(event, never()).sendUpstream(any());

    // And: result is successful
    assertInstanceOf(SuccessResponse.class, result);
  }

  @Test
  void shouldComposeActivityFeatures() throws Exception {
    // Given: features uuid
    String initialUuid = "initialUuid";
    String newUuid = "newUuid";

    // And: old version of feature
    String featureId = "featureId";
    NakshaFeature oldFeature = nakshaFeature(featureId)
        .withUuid(initialUuid)
        .withPuuid(null)
        .withNuuid(newUuid)
        .withAction(Action.CREATED)
        .withCustomProperties(
            Map.of(
                "op", "old feature",
                "magicNumber", 123
            )
        ).build();

    // And: new version of feature
    NakshaFeature newFeature = nakshaFeature(featureId)
        .withUuid(newUuid)
        .withPuuid(initialUuid)
        .withNuuid(null)
        .withAction(Action.UPDATED)
        .withCustomProperties(Map.of(
            "op", "new feature",
            "magicBoolean", true
        ))
        .build();

    // And: space storage that returns these features for some ReadFeatures request
    ReadFeatures request = new ReadFeatures();
    spaceStorageSessionReturningHistoryFeatures(request, oldFeature, newFeature);

    // When: handler processes given request
    Response result = handler.processEvent(eventWith(request));

    // Then: result contains activity log calculated on basis of these features
    assertThatResult(result)
        .hasActivityFeatures(
            firstFeature -> firstFeature
                .hasId(uuid(newFeature))
                .hasActivityLogId(featureId)
                .hasAction(Action.UPDATED.toString())
                .hasReversePatch("""
                    {
                      "add": 1,
                      "remove": 1,
                      "replace": 1,
                      "ops": [
                        {
                          "op": "replace",
                          "path": "/properties/op",
                          "value": "old feature"
                        },
                        {
                          "op": "add",
                          "path": "/properties/magicNumber",
                          "value": 123
                        },
                        {
                          "op": "remove",
                          "path": "/properties/magicBoolean"
                        }
                      ]
                    }
                    """),
            secondFeature -> secondFeature
                .hasId(uuid(oldFeature))
                .hasActivityLogId(featureId)
                .hasAction(Action.CREATED.toString())
                .hasReversePatch(null)
        );
  }

  @Test
  void shouldNotCalculateReversePatchAfterCreation() throws Exception {
    // Given: ReadFeatures request
    ReadFeatures request = new ReadFeatures();

    // And: space storage that returns some feature with 'CREATE' action for given request
    spaceStorageSessionReturningHistoryFeatures(request, nakshaFeature("featureId")
        .withUuid("uuid")
        .withPuuid(null)
        .withAction(Action.CREATED)
        .build());

    // When: handler processes event bearing such request
    Response result = handler.processEvent(eventWith(request));

    // Then: result does not bear any reverse patch
    assertThatResult(result)
        .hasActivityFeatures(feature -> feature
            .hasAction(Action.CREATED.toString())
            .hasId("uuid")
            .hasActivityLogId("featureId")
            .hasReversePatch(null)
        );
  }

  @Test
  void shouldNotCalculateDiffAfterDeletion() throws Exception {
    // Given: ReadFeatures request
    ReadFeatures request = new ReadFeatures();

    // And: space storage that returns features with 'DELETE' and `CREATE` actions for given request
    spaceStorageSessionReturningHistoryFeatures(request,
        nakshaFeature("featureId")
            .withUuid("delete_uuid")
            .withPuuid("create_uuid")
            .withAction(Action.DELETED)
            .withCreatedAt(T0)
            .withUpdatedAt(T1)
            .build(),
        nakshaFeature("featureId")
            .withUuid("create_uuid")
            .withPuuid(null)
            .withAction(Action.CREATED)
            .withCreatedAt(T0)
            .withUpdatedAt(T0)
            .build()
    );

    // When: handler processes event bearing such request
    Response result = handler.processEvent(eventWith(request));

    // Then: there is no reverse patch for any of these features
    assertThatResult(result)
        .hasActivityFeatures(
            first -> first
                .hasAction(Action.DELETED.toString())
                .hasId("delete_uuid")
                .hasActivityLogId("featureId")
                .hasReversePatch(null),
            second -> second
                .hasAction(Action.CREATED.toString())
                .hasId("create_uuid")
                .hasActivityLogId("featureId")
                .hasReversePatch(null)
        );
  }

  @Test
  void shouldBeAlignedWithDataHubSamples() throws Exception {
    // Given:
    ActivityLogHandler handlerWithSampleSpace = handlerForSpaceId(DatahubSamplesUtil.SAMPLE_SPACE_ID);

    // And:
    DatahubSample datahubSample = DatahubSamplesUtil.loadDatahubSample();

    // And:
    ReadFeatures request = new ReadFeatures();

    // And:
    spaceStorageSessionReturningHistoryFeatures(request, datahubSample.historyFeatures());

    // And
    IEvent event = eventWith(request);

    // When
    Response result = handlerWithSampleSpace.processEvent(event);

    // Then
    assertThatResult(result).hasActivityFeaturesIdenticalTo(datahubSample.activityFeatures());
  }

  private ActivityLogHandler handlerForSpaceId(String spaceId) {
    when(eventHandler.getProperties()).thenReturn(activityLogHandlerProperties(spaceId));
    return new ActivityLogHandler(eventHandler, naksha);
  }

  private IReadSession spaceStorageSessionReturningHistoryFeatures(ReadRequest handledRequest, NakshaFeature... historyFeatures) {
    return spaceStorageSessionReturningHistoryFeatures(handledRequest, List.of(historyFeatures));
  }

  private IReadSession spaceStorageSessionReturningHistoryFeatures(ReadRequest handledRequest, List<NakshaFeature> historyFeatures) {
    IReadSession readSession = mock(IReadSession.class);
    when(readSession.execute(handledRequest)).thenReturn(new SuccessResponse(NakshaFeatureList.fromList(historyFeatures)));
    when(spaceStorage.newReadSession(any())).thenReturn(readSession);
    return readSession;
  }

  private IEvent eventWith(Request request) {
    IEvent event = Mockito.mock(IEvent.class);
    when(event.getRequest()).thenReturn(request);
    return event;
  }

  private static String uuid(NakshaFeature newFeature) {
    return newFeature.getProperties().getXyz().getUuid();
  }

  private static Stream<Request> unhandledRequests() {
    return Stream.of(
        new WriteRequest().add(new Write().createFeature(null,"some_collection",new NakshaFeature("some_feature"))),
        new ReadCollections()
    );
  }
}
