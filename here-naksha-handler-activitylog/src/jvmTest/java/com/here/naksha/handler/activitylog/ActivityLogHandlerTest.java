package com.here.naksha.handler.activitylog;

import static com.here.naksha.handler.activitylog.ActivityLogHandlerProperties.activityLogHandlerProperties;
import static com.here.naksha.handler.activitylog.GuidUtil.guid;
import static com.here.naksha.handler.activitylog.GuidUtil.randomVersion;
import static com.here.naksha.handler.activitylog.NakshaFeatureBuilder.nakshaFeature;
import static com.here.naksha.handler.activitylog.assertions.ActivityLogSuccessResultAssertions.assertThatResult;
import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import naksha.base.*;
import naksha.model.Action;
import naksha.model.Guid;
import naksha.model.IReadSession;
import naksha.model.IStorage;
import naksha.model.NakshaContext;
import naksha.model.NakshaError;
import naksha.model.TupleNumber;
import naksha.model.Version;
import naksha.model.XyzNs;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaProperties;
import naksha.model.objects.XyzMembers;
import naksha.model.request.ErrorResponse;
import naksha.model.request.ReadCollections;
import naksha.model.request.ReadFeatures;
import naksha.model.request.ReadRequest;
import naksha.model.request.Request;
import naksha.model.request.Response;
import naksha.model.request.SuccessResponse;
import naksha.model.request.Write;
import naksha.model.request.WriteRequest;
import naksha.model.request.query.AnyOp;
import naksha.model.request.query.IMemberQuery;
import naksha.model.request.query.MemberQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.ArgumentMatcher;
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
    IEvent event = eventWith(new WriteRequest().add(new Write().deleteCollectionById(null, "some_collection")));

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
    String featureId = "featureId";
    Guid initialUuid = guid(featureId, Version.now(new JvmInt64(1),Action.CREATE));
    Guid newUuid = guid(featureId, Version.now(new JvmInt64(2),Action.UPDATE));

    // And: old version of feature
    NakshaFeature oldFeature = nakshaFeature(featureId)
        .withUuid(initialUuid.toString())
        .withPuuid(null)
        .withNuuid(newUuid.toString())
        .withAction(Action.CREATE)
        .withCustomProperties(
            Map.of(
                "op", "old feature",
                "magicNumber", 123
            )
        ).build();

    // And: new version of feature
    NakshaFeature newFeature = nakshaFeature(featureId)
        .withUuid(newUuid.toString())
        .withPuuid(initialUuid.toString())
        .withNuuid(null)
        .withAction(Action.UPDATE)
        .withCustomProperties(Map.of(
            "op", "new feature",
            "magicBoolean", true
        ))
        .build();

    // And: space storage that returns these features for some ReadFeatures request
    configureSpaceStorage(
        initialHistoryAwareRequestReturns(List.of(newFeature)),
        requestForMissingPredecessorsReturns(List.of(oldFeature))
    );

    // When: handler processes given request
    Response result = handler.processEvent(eventWith(new ReadFeatures()));

    // Then: result contains single feature (made of the two configured above)
    assertThatResult(result)
        .hasActivityFeatures(
            singleFeature -> singleFeature
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
                    """)
        );
  }

  @Test
  void shouldNotCalculateReversePatchAfterCreation() throws Exception {
    // Given
    String featureId = "featureId";
    Guid createdGuid = guid(featureId, Version.now(new JvmInt64(0), Action.CREATE));

    // And: space storage that returns only some feature with 'CREATE' action
    configureSpaceStorage(
        initialHistoryAwareRequestReturns(List.of(
            nakshaFeature(featureId)
                .withUuid(createdGuid.toString())
                .withPuuid(null)
                .withAction(Action.CREATE)
                .build())
        ),
        requestForMissingPredecessorsReturns(emptyList())
    );

    // When: handler processes event bearing such request
    Response result = handler.processEvent(eventWith(new ReadFeatures()));

    // Then: result does not bear any reverse patch
    assertThatResult(result)
        .hasActivityFeatures(feature -> feature
            .hasAction(Action.CREATE.toString())
            .hasId(createdGuid.toString())
            .hasActivityLogId(featureId)
            .hasReversePatch(null)
        );
  }

  @Test
  void shouldNotCalculateDiffAfterDeletion() throws Exception {
    // Given
    String featureId = "featureId";
    Timestamp ts0 = Timestamp.fromMillis(T0);
    Timestamp ts1 = Timestamp.fromMillis(T1);
    Version createdVersion = Version.auto(ts0.getYear(), ts0.getMonth(), ts0.getDay(), new JvmInt64(0), Action.CREATE);
    Version deletedVersion = Version.auto(ts1.getYear(), ts1.getMonth(), ts1.getDay(), new JvmInt64(1), Action.DELETE);
    Guid createdGuid = guid(featureId, createdVersion);
    Guid deletedGuid = guid(featureId, deletedVersion);

    // And: space storage that returns features with 'DELETE' and `CREATE` actions
    configureSpaceStorage(
        initialHistoryAwareRequestReturns(List.of(
            nakshaFeature(featureId)
                .withUuid(deletedGuid.toString())
                .withPuuid(createdGuid.toString())
                .withAction(Action.DELETE)
                .withCreatedAt(T0)
                .withUpdatedAt(T1)
                .build()
        )),
        requestForMissingPredecessorsReturns(List.of(
            nakshaFeature("featureId")
                .withUuid(createdGuid.toString())
                .withPuuid(null)
                .withAction(Action.CREATE)
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
                .hasAction(Action.DELETE.toString())
                .hasId(deletedGuid.toString())
                .hasActivityLogId(featureId)
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
  void shouldFailIfInitialRequestFails() {
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
  void shouldFailIfRequestForMissingPredecessorsFails() {
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

  @Test
  void shouldFetchPredecessorViaNextVersionLookup() {
    // Given: one root feature missing its predecessor in the initial fetch
    List<ReadRequest> predecessorRequests = new ArrayList<>();
    Guid uuid = guid("sample_feature", randomVersion());
    configureSpaceStorage(
        initialHistoryAwareRequestReturns(List.of(featureWithUuidOnly(uuid.toString()))),
        capturingOnlyPredecessorRequest(predecessorRequests)
    );

    // When: handler processes event bearing such request
    handler.processEvent(eventWith(new ReadFeatures()));

    // Then: a single next_version meta-query is issued with the root's own version
    assertEquals(1, predecessorRequests.size());
    ReadFeatures predecessorReq = assertInstanceOf(ReadFeatures.class, predecessorRequests.get(0));
    assertTrue(containsNextVersionMetaQuery(predecessorReq, uuid.tupleNumber));
  }

  @Test
  void shouldBundleAllMissingPredecessorLookupsIntoSingleQuery() {
    // Given: two root features, neither paired via nuuid in the initial fetch
    List<ReadRequest> predecessorRequests = new ArrayList<>();
    Guid firstUuid = guid("sample_feature_1", randomVersion());
    Guid secondUuid = guid("sample_feature_2", randomVersion());
    configureSpaceStorage(
        initialHistoryAwareRequestReturns(List.of(
            featureWithUuidOnly(firstUuid.toString()),
            featureWithUuidOnly(secondUuid.toString())
        )),
        capturingOnlyPredecessorRequest(predecessorRequests)
    );

    // When: handler processes event bearing such request
    handler.processEvent(eventWith(new ReadFeatures()));

    // Then: both predecessors are looked up in ONE next_version query
    assertEquals(1, predecessorRequests.size());
    ReadFeatures predecessorReq = assertInstanceOf(ReadFeatures.class, predecessorRequests.get(0));
    assertTrue(containsNextVersionMetaQuery(predecessorReq, firstUuid.tupleNumber, secondUuid.tupleNumber));
  }

  private NakshaFeature featureWithUuidOnly(String uuid) {
    return featureWithXyzFields(Map.of(
        XyzNs.UUID, uuid
    ));
  }

  private NakshaFeature featureWithXyzFields(Map<String, Object> fields) {
    XyzNs xyzNs = new XyzNs();
    fields.forEach((k, v) -> xyzNs.put(k, v));
    NakshaProperties props = new NakshaProperties();
    props.setXyz(xyzNs);
    return new NakshaFeature().withProperties(props);
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

  private void configureSpaceStorage(ReadBehavior... readBehaviors) {
    IReadSession readSession = mockReadSession(readBehaviors);
    when(spaceStorage.newReadSession(any())).thenReturn(readSession);
  }

  private ReadBehavior initialHistoryAwareRequestReturns(List<NakshaFeature> nakshaFeatures) {
    return ReadBehavior.successfulRead(initialRequestMatcher(), nakshaFeatures);
  }

  private ReadBehavior requestForMissingPredecessorsReturns(List<NakshaFeature> nakshaFeatures) {
    return ReadBehavior.successfulRead(predecessorRequestMatcher(), nakshaFeatures);
  }

  private ReadBehavior initialHistoryRequestFails() {
    return ReadBehavior.failingRead(initialRequestMatcher());
  }

  private ReadBehavior requestForMissingPredecessorsFails() {
    return ReadBehavior.failingRead(predecessorRequestMatcher());
  }

  private ReadBehavior capturingOnlyPredecessorRequest(List<ReadRequest> capturedReqs) {
    return ReadBehavior.capturingRead(predecessorRequestMatcher(), capturedReqs);
  }

  private ArgumentMatcher<ReadRequest> initialRequestMatcher() {
    return readRequest -> isHistoryAwareReadFeatures(readRequest)
                          && !containsNextVersionMetaQuery((ReadFeatures) readRequest);
  }

  private ArgumentMatcher<ReadRequest> predecessorRequestMatcher() {
    return readRequest -> isHistoryAwareReadFeatures(readRequest)
                          && containsNextVersionMetaQuery((ReadFeatures) readRequest);
  }

  private boolean isHistoryAwareReadFeatures(ReadRequest readRequest) {
    if (readRequest instanceof ReadFeatures rf) {
      return rf.getQueryHistory();
    }
    return false;
  }

  private boolean containsNextVersionMetaQuery(ReadFeatures readFeatures, TupleNumber... expectedTns) {
    IMemberQuery metaQuery = readFeatures.getQuery().getMembers();
    if (!(metaQuery instanceof MemberQuery mq)) return false;
    boolean basicCheck = ((Proxy)XyzMembers.XyzNextVersion).equals(mq.getMember())
                         && mq.getOp().equals(AnyOp.IS_ANY_OF);
    if (!basicCheck) return false;
    if (expectedTns.length == 0) return mq.getValue() != null;
    // next_version is an int8 column — the query value is an Int64[] or AnyList of version values
    // (NullableProperty may convert a Java array to an AnyList when storing)
    Object value = mq.getValue();
    List<Int64> versions;
    if (value instanceof Int64[] arr) {
      versions = Arrays.asList(arr);
    } else if (value instanceof AnyList list) {
      versions = new java.util.ArrayList<>();
      for (int i = 0; i < list.getSize(); i++) {
        Object item = list.get(i);
        if (item instanceof Int64 v) versions.add(v);
        else return false;
      }
    } else {
      return false;
    }
    if (versions.size() != expectedTns.length) return false;
    return Arrays.stream(expectedTns)
        .map(tn -> tn.version)
        .allMatch(expected -> versions.stream().anyMatch(expected::equals));
  }

  private IReadSession mockReadSession(ReadBehavior... readBehavior) {
    IReadSession readSession = mock(IReadSession.class);
    for (ReadBehavior behavior : readBehavior) {
      when(readSession.execute(argThat(behavior.readReqMatcher))).thenReturn(behavior.readResponse);
      when(readSession.execute(argThat(behavior.readReqMatcher))).then(invocation -> {
        ReadRequest arg = invocation.getArgument(0);
        if (behavior.getCaptured() != null) {
          behavior.getCaptured().add(arg);
        }
        return behavior.readResponse;
      });
    }
    return readSession;
  }

  private static class ReadBehavior {

    private final ArgumentMatcher<ReadRequest> readReqMatcher;
    private final Response readResponse;
    private List<ReadRequest> captured;

    private ReadBehavior(ArgumentMatcher<ReadRequest> readReqMatcher, Response readResponse) {
      this.readReqMatcher = readReqMatcher;
      this.readResponse = readResponse;
    }

    public ReadBehavior(ArgumentMatcher<ReadRequest> readReqMatcher, Response readResponse, List<ReadRequest> captured) {
      this.readReqMatcher = readReqMatcher;
      this.readResponse = readResponse;
      this.captured = captured;
    }

    public List<ReadRequest> getCaptured() {
      return captured;
    }

    static ReadBehavior successfulRead(ArgumentMatcher<ReadRequest> readReqMatcher, List<NakshaFeature> returnedFeatures) {
      return new ReadBehavior(readReqMatcher, new SuccessResponse(returnedFeatures));
    }

    static ReadBehavior failingRead(ArgumentMatcher<ReadRequest> readReqMatcher) {
      return new ReadBehavior(readReqMatcher, new ErrorResponse());
    }

    static ReadBehavior capturingRead(ArgumentMatcher<ReadRequest> readReqMatcher, List<ReadRequest> captured) {
      return new ReadBehavior(
          readReqMatcher,
          new SuccessResponse(), // we should not care about this result
          captured
      );
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
