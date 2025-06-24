package com.here.naksha.handler.activitylog;

import static com.here.naksha.handler.activitylog.ActivityLogHandlerProperties.activityLogHandlerProperties;
import static com.here.naksha.handler.activitylog.ActivityLogRequestTranslationUtil.*;
import static com.here.naksha.handler.activitylog.NakshaFeatureBuilder.nakshaFeature;
import static com.here.naksha.handler.activitylog.assertions.ActivityLogSuccessResultAssertions.assertThatResult;
import static com.here.naksha.test.common.assertions.PropertyQueryAssertions.assertThatPropertyQuery;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.AdditionalMatchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.here.naksha.handler.activitylog.sample.DatahubSamplesUtil;
import com.here.naksha.handler.activitylog.sample.DatahubSamplesUtil.DatahubSample;
import com.here.naksha.lib.core.IEvent;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.models.naksha.EventHandlerConfig;
import com.here.naksha.test.common.assertions.PropertyQueryAssertions;
import naksha.base.JvmInt64;
import naksha.model.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaFeatureList;
import naksha.model.request.*;
import naksha.model.request.query.IPropertyQuery;
import naksha.model.request.query.POr;
import naksha.model.request.query.PQuery;
import naksha.model.request.query.Property;
import naksha.model.request.query.StringOp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.ArgumentCaptor;
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
  void shouldTransformReadRequest() {
    // Given: Original read request
    String featureUuid = "featureUuid";
    String featureId = "featureId";
    ReadFeatures originalReadFeatures = new ReadFeatures();
    originalReadFeatures.setVersions(1);
    originalReadFeatures.getQuery().setProperties(
            new POr(
                new PQuery(new Property(NakshaFeature.ID_KEY), StringOp.EQUALS, featureUuid),
                    new PQuery(PROPERTY_ACTIVITY_LOG_ID, StringOp.EQUALS, featureId)
            )
        );

    // And: Configured session that will receive read request from handler
    IReadSession readSession = mock(IReadSession.class);
    when(spaceStorage.newReadSession(any())).thenReturn(readSession);
    when(readSession.execute(any())).thenReturn(new SuccessResponse());

    // When: Processing event with original request
    handler.processEvent(eventWith(originalReadFeatures));

    // Then: Some request was executed by the session
    ArgumentCaptor<ReadFeatures> requestCaptor = ArgumentCaptor.forClass(ReadFeatures.class);
    verify(readSession).execute(requestCaptor.capture());

    // And: The executed request was a ReadFeatures transformed by handler
    ReadFeatures requestPassedToSpaceStorage = requestCaptor.getValue();
    assertEquals(List.of(SPACE_ID), requestPassedToSpaceStorage.getCollectionIds(),
        "Transformed request should use 'spaceId' from handler's properties");
    assertEquals(Integer.MAX_VALUE,requestPassedToSpaceStorage.getVersions(), "Transformed request should return all versions of feature");
    assertThatPropertyQuery(requestPassedToSpaceStorage.getQuery().getProperties()) // POp for id and activityLogId should be transformed
        .hasChildrenThat(
            first -> first
                .hasOp(StringOp.EQUALS)
                .hasProperty(PROPERTY_UUID)
                .hasValue(featureUuid),
            second -> second
                .hasOp(StringOp.EQUALS)
                .hasProperty(List.of(NakshaFeature.ID_KEY))
                .hasValue(featureId)
        );
  }

  @Test
  void shouldComposeActivityFeatures() throws Exception {
    // Given: old version of feature
    String featureId = "featureId";
    NakshaFeature oldFeature = nakshaFeature(featureId)
        .withUuid("initial_uuid")
        .withPuuid(null)
        .withAction(Action.CREATED)
        .withCustomProperties(
            Map.of(
                "op", "old feature",
                "magicNumber", 123
            )
        ).build();

    // And: new version of feature
    NakshaFeature newFeature = nakshaFeature(featureId)
        .withUuid("new_uuid")
        .withPuuid("initial_uuid")
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
  void shouldFetchAdditionalHistoryFeaturesWhenNeeded() throws Exception {
    // Given: Client request (we don't care about it's specifics)
    ReadFeatures firstRequest = new ReadFeatures();

    // And: Space storage that will return two history features for client's request
    IReadSession readSession = spaceStorageSessionReturningHistoryFeatures(firstRequest,
        nakshaFeature("id_1")
            .withUuid("uuid_1")
            .withPuuid("puuid_1")
            .withAction(Action.UPDATED)
            .build(),
        nakshaFeature("id_2")
            .withUuid("uuid_2")
            .withPuuid("puuid_2")
            .withAction(Action.DELETED)
            .build()
    );

    // And: Space storage that will return two predecessors for any other request
    when(readSession.execute(not(eq(firstRequest)))).thenReturn(new SuccessResponse(NakshaFeatureList.fromList(List.of(
        nakshaFeature("id_1")
            .withUuid("puuid_1")
            .withPuuid(null)
            .withAction(Action.CREATED)
            .build(),
        nakshaFeature("id_2")
            .withUuid("puuid_2")
            .withPuuid(null)
            .withAction(Action.CREATED)
            .build()
    ))));

    // When: Handler processes event with original client's request
    Response result = handler.processEvent(eventWith(firstRequest));

    // Then: Space storage should be queried twice
    ArgumentCaptor<ReadFeatures> requestCaptor = ArgumentCaptor.forClass(ReadFeatures.class);
    verify(readSession, times(2)).execute(requestCaptor.capture());

    // And: First request passed to the space should be the client one
    List<ReadFeatures> requestPassedToSpace = requestCaptor.getAllValues();
    assertEquals(2, requestPassedToSpace.size());
    assertEquals(firstRequest, requestPassedToSpace.get(0));

    // And: Second request passed to space should be about fetching additional predecessors
    ReadFeatures secondRequest = requestPassedToSpace.get(1);
    assertEquals(Integer.MAX_VALUE,secondRequest.getVersions());
    assertEquals(List.of(SPACE_ID), secondRequest.getCollectionIds());
    PropertyQueryAssertions.assertThatPropertyQuery(secondRequest.getQuery().getProperties())
        .isPOr()
        .hasChildrenThat(
            first -> first
                .hasProperty(PROPERTY_UUID)
                .hasOp(StringOp.EQUALS)
                .hasValue("puuid_2"),
            second -> second
                .hasProperty(PROPERTY_UUID)
                .hasOp(StringOp.EQUALS)
                .hasValue("puuid_1")
        );

    // And: Handler's result should only contain features from the first response (to client's request)
    assertThatResult(result)
        .hasActivityFeatures(
            first -> first
                .hasId("uuid_2")
                .hasActivityLogId("id_2")
                .hasAction(Action.DELETED.toString()),
            second -> second
                .hasId("uuid_1")
                .hasActivityLogId("id_1")
                .hasAction(Action.UPDATED.toString())
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

  IPropertyQuery propertyQuery = readFeatures.getQuery().getProperties();
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
