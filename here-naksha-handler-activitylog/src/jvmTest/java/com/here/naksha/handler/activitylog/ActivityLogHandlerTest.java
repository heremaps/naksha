package com.here.naksha.handler.activitylog;

import static com.here.naksha.handler.activitylog.ActivityLogHandlerProperties.activityLogHandlerProperties;
import static com.here.naksha.handler.activitylog.ActivityLogRequestTranslationUtil.PROPERTY_ACTIVITY_LOG_ID;
import static com.here.naksha.handler.activitylog.NakshaFeatureBuilder.nakshaFeature;
import static com.here.naksha.handler.activitylog.assertions.ActivityLogSuccessResultAssertions.assertThatResult;
import static com.here.naksha.test.common.assertions.PropertyQueryAssertions.assertThatPropertyQuery;
import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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
import com.here.naksha.test.common.assertions.PropertyQueryAssertions;
import naksha.base.JvmInt64;
import naksha.base.NakshaError;
import naksha.model.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import naksha.base.AnyList;
import naksha.model.Action;
import naksha.model.IReadSession;
import naksha.model.IStorage;
import naksha.model.NakshaContext;
import naksha.base.NakshaError;
import naksha.model.objects.NakshaFeature;
import naksha.model.request.ErrorResponse;
import naksha.model.request.ReadCollections;
import naksha.model.request.ReadFeatures;
import naksha.model.request.ReadRequest;
import naksha.model.request.Request;
import naksha.model.request.Response;
import naksha.model.request.SuccessResponse;
import naksha.model.request.Write;
import naksha.model.request.WriteRequest;
import naksha.model.request.query.*;
import naksha.mom.v2.MomProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.*;

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

  private AutoCloseable mockCloseable;
  private ActivityLogHandler handler;

  @BeforeEach
  void setup() {
    mockCloseable = MockitoAnnotations.openMocks(this);
    when(naksha.getSpaceStorage()).thenReturn(spaceStorage);
    doCallRealMethod().when(spaceStorage).runInReadSession(any(), any());
    doCallRealMethod().when(spaceStorage).useReadSession(any(), any());
    doCallRealMethod().when(spaceStorage).useWriteSession(any(), any());
    doCallRealMethod().when(spaceStorage).runInWriteSession(any(), any());
    handler = handlerForSpaceId(SPACE_ID);
    NakshaContext.currentContext().withAppId("test-app");
  }

  @AfterEach
  void teardown() {
    try {
      mockCloseable.close();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
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

  static final Property PROPERTY_UUID = new Property("uuid");

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
    NakshaFeature oldFeature = nakshaFeature(
        featureId,
        "initial_uuid",
        null,
        Action.CREATE,
        Map.of(
            "op", "old feature",
            "magicNumber", 123
        )
    );

    // And: new version of feature
    NakshaFeature newFeature = nakshaFeature(
        featureId,
        "new_uuid",
        "initial_uuid",
        Action.UPDATE,
        Map.of(
            "op", "new feature",
            "magicBoolean", true
        )
    );

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
                .hasAction(Action.UPDATE.toString())
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
                .hasAction(Action.CREATE.toString())
                .hasReversePatch(null)
        );
  }

  @Test
  void shouldFetchAdditionalHistoryFeaturesWhenNeeded() throws Exception {
    // Given: Client request (we don't care about it's specifics)
    ReadFeatures firstRequest = new ReadFeatures();

    // And: Space storage that will return two history features for client's request
    IReadSession readSession = spaceStorageSessionReturningHistoryFeatures(firstRequest,
        nakshaFeature("id_1", "uuid_1", "puuid_1", Action.UPDATE),
        nakshaFeature("id_2", "uuid_2", "puuid_2", Action.DELETE)
    );

    // And: Space storage that will return two predecessors for any other request
    when(readSession.execute(not(eq(firstRequest)))).thenReturn(new SuccessResponse(NakshaFeatureList.fromList(List.of(
        nakshaFeature("id_1", "puuid_1", null, Action.CREATE),
        nakshaFeature("id_2", "puuid_2", null, Action.CREATE)
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
    assertThatPropertyQuery(secondRequest.getQuery().getProperties())
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
                .hasAction(Action.DELETE.toString()),
            second -> second
                .hasId("uuid_1")
                .hasActivityLogId("id_1")
                .hasAction(Action.UPDATE.toString())
        );
  }

  @Test
  void shouldNotCalculateReversePatchAfterCreation() throws Exception {
    // Given: ReadFeatures request
    ReadFeatures request = new ReadFeatures();

    // And: space storage that returns some feature with 'CREATE' action for given request
    spaceStorageSessionReturningHistoryFeatures(request, nakshaFeature(
        "featureId",
        "uuid",
        null,
        Action.CREATE
    ));

    // When: handler processes event bearing such request
    Response result = handler.processEvent(eventWith(request));

    // Then: result does not bear any reverse patch
    assertThatResult(result)
        .hasActivityFeatures(feature -> feature
            .hasAction(Action.CREATE.toString())
            .hasId("uuid")
            .hasActivityLogId("featureId")
            .hasReversePatch(null)
        );
  }

  @Test
  void shouldNotCalculateDiffAfterDeletion() throws Exception {
    // Given: space storage that returns features with 'DELETE' and `CREATE` actions
    configureSpaceStorage(
        initialHistoryAwareRequestReturns(List.of(
            nakshaFeature("featureId")
                .withUuid("delete_uuid")
                .withPuuid("create_uuid")
                .withAction(Action.DELETED)
                .withCreatedAt(T0)
                .withUpdatedAt(T1)
                .build()
        )),
        requestForMissingPredecessorsReturns(List.of(
            nakshaFeature("featureId")
                .withUuid("create_uuid")
                .withPuuid(null)
                .withAction(Action.CREATED)
                .withCreatedAt(T0)
                .withUpdatedAt(T0)
                .build()
        ))
    );

    // When: handler processes event bearing such request
    Response result = handler.processEvent(eventWith(new ReadFeatures()));

    // Then: there is no reverse patch for any of these features
    assertThatResult(result)
        .hasActivityFeatures(
            feature -> feature
                .hasAction(Action.DELETED.toString())
                .hasId("delete_uuid")
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
    spaceStorageReturnsAllNecessaryFeatures(datahubSample.historyFeatures());

    // When
    Response result = handlerWithSampleSpace.processEvent(eventWith(new ReadFeatures()));

    // Then
    assertThatResult(result).hasActivityFeaturesIdenticalTo(datahubSample.activityFeatures());
  }

  @Test
  void shouldFailIfInitialRequestFails(){
    // Given
    configureSpaceStorage(
        initialHistoryRequestFails(),
        requestForMissingPredecessorsReturns(List.of(new NakshaFeature("some_id")))
    );

    // When: handler processes event bearing such request
    Response result = handler.processEvent(eventWith(new ReadFeatures()));

    // Then:
    Assertions.assertInstanceOf(ErrorResponse.class, result);
  }

  @Test
  void shouldFailIfRequestForMissingPredecessorsFails(){
    // Given
    configureSpaceStorage(
        initialHistoryAwareRequestReturns(List.of(new NakshaFeature("some_id"))),
        requestForMissingPredecessorsFails()
    );

    // When: handler processes event bearing such request
    Response result = handler.processEvent(eventWith(new ReadFeatures()));

    // Then:
    Assertions.assertInstanceOf(ErrorResponse.class, result);
  }

  private ActivityLogHandler handlerForSpaceId(String spaceId) {
    when(eventHandler.getProperties()).thenReturn(activityLogHandlerProperties(spaceId));
    return new ActivityLogHandler(eventHandler, naksha);
  }

  private void spaceStorageReturnsAllNecessaryFeatures(List<NakshaFeature> historyFeatures) {
    configureSpaceStorage(
        initialHistoryAwareRequestReturns(historyFeatures),
        requestForMissingPredecessorsReturns(emptyList())
    );
  }

  private void configureSpaceStorage(ReadBehavior... readBehaviors){
    IReadSession readSession = mockReadSession(readBehaviors);
    when(spaceStorage.newReadSession(any())).thenReturn(readSession);
  }

  private ReadBehavior initialHistoryAwareRequestReturns(List<NakshaFeature> nakshaFeatures){
    ArgumentMatcher<ReadRequest> notNextTnBasedHistoryQuery = readRequest -> {
      return isHistoryAwareReadFeatures(readRequest) && !containsNextVersionMetaQuery((ReadFeatures) readRequest);
    };
    return ReadBehavior.successfulRead(notNextTnBasedHistoryQuery, nakshaFeatures);
  }

  private ReadBehavior requestForMissingPredecessorsReturns(List<NakshaFeature> nakshaFeatures) {
    ArgumentMatcher<ReadRequest> anyNextTnBasedRequest = readRequest -> {
      return isHistoryAwareReadFeatures(readRequest)
             && containsNextVersionMetaQuery((ReadFeatures) readRequest);
    };
    return ReadBehavior.successfulRead(anyNextTnBasedRequest, nakshaFeatures);
  }

  private ReadBehavior initialHistoryRequestFails(){
    ArgumentMatcher<ReadRequest> notNextTnBasedHistoryQuery = readRequest -> {
      return isHistoryAwareReadFeatures(readRequest) && !containsNextVersionMetaQuery((ReadFeatures) readRequest);
    };
    return ReadBehavior.failingRead(notNextTnBasedHistoryQuery);
  }

  private ReadBehavior requestForMissingPredecessorsFails() {
    ArgumentMatcher<ReadRequest> anyNextTnBasedRequest = readRequest -> {
      return isHistoryAwareReadFeatures(readRequest)
             && containsNextVersionMetaQuery((ReadFeatures) readRequest);
    };
    return ReadBehavior.failingRead(anyNextTnBasedRequest);
  }

  private boolean isHistoryAwareReadFeatures(ReadRequest readRequest) {
    if (readRequest instanceof ReadFeatures rf) {
      return rf.getQueryHistory() && rf.getCollectionIds().size() == 1;
    }
    return false;
  }

  private boolean containsNextVersionMetaQuery(ReadFeatures readFeatures){
    IMetaQuery metaQuery = readFeatures.getQuery().getMetadata();
    if (metaQuery instanceof MetaQuery mq) {
      return mq.getColumn().equals(MetaColumn.nextVersion())
             && mq.getOp().equals(AnyOp.IS_ANY_OF)
             && mq.getValue() instanceof AnyList;
    }
    return false;
  }

  private IReadSession mockReadSession(ReadBehavior... readBehavior) {
    IReadSession readSession = mock(IReadSession.class);
    for (ReadBehavior behavior : readBehavior) {
      when(readSession.execute(argThat(behavior.readReqMatcher))).thenReturn(behavior.readResponse);
    }
    return readSession;
  }

  private record ReadBehavior(ArgumentMatcher<ReadRequest> readReqMatcher, Response readResponse) {

    static ReadBehavior successfulRead(ArgumentMatcher<ReadRequest> readReqMatcher, List<NakshaFeature> returnedFeatures) {
      return new ReadBehavior(readReqMatcher, new SuccessResponse(returnedFeatures));
    }

    static ReadBehavior failingRead(ArgumentMatcher<ReadRequest> readReqMatcher) {
      return new ReadBehavior(readReqMatcher, new ErrorResponse());
    }
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
        new WriteRequest().add(new Write().createFeature(null, "some_collection", new NakshaFeature("some_feature"))),
        new ReadCollections()
    );
  }
}
