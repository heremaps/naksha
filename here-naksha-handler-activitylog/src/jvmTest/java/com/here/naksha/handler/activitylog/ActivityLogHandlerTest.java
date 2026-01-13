package com.here.naksha.handler.activitylog;

import static com.here.naksha.handler.activitylog.ActivityLogHandlerProperties.activityLogHandlerProperties;
import static com.here.naksha.handler.activitylog.GuidUtil.guid;
import static com.here.naksha.handler.activitylog.GuidUtil.randomVersion;
import static com.here.naksha.handler.activitylog.NakshaFeatureBuilder.nakshaFeature;
import static com.here.naksha.handler.activitylog.assertions.ActivityLogSuccessResultAssertions.assertThatResult;
import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import naksha.base.AnyList;
import naksha.base.JvmInt64;
import naksha.model.Action;
import naksha.model.Guid;
import naksha.model.GuidList;
import naksha.model.IReadSession;
import naksha.model.IStorage;
import naksha.model.NakshaContext;
import naksha.model.NakshaError;
import naksha.model.TupleNumber;
import naksha.model.TupleNumberVariant;
import naksha.model.Version;
import naksha.model.XyzNs;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaProperties;
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
import naksha.model.request.query.IMetaQuery;
import naksha.model.request.query.MetaColumn;
import naksha.model.request.query.MetaQuery;
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
    Guid initialUuid = guid(featureId, new Version(1));
    Guid newUuid = guid(featureId, new Version(2));

    // And: old version of feature
    NakshaFeature oldFeature = nakshaFeature(featureId)
        .withUuid(initialUuid.toString())
        .withPuuid(null)
        .withNuuid(newUuid.toString())
        .withAction(Action.CREATED)
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
        .withAction(Action.UPDATED)
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
                    """)
        );
  }

  @Test
  void shouldNotCalculateReversePatchAfterCreation() throws Exception {
    // Given
    String featureId = "featureId";
    Guid createdGuid = guid(featureId, new Version(0));

    // And: space storage that returns only some feature with 'CREATE' action
    configureSpaceStorage(
        initialHistoryAwareRequestReturns(List.of(
            nakshaFeature(featureId)
                .withUuid(createdGuid.toString())
                .withPuuid(null)
                .withAction(Action.CREATED)
                .build())
        ),
        requestForMissingPredecessorsReturns(emptyList())
    );

    // When: handler processes event bearing such request
    Response result = handler.processEvent(eventWith(new ReadFeatures()));

    // Then: result does not bear any reverse patch
    assertThatResult(result)
        .hasActivityFeatures(feature -> feature
            .hasAction(Action.CREATED.toString())
            .hasId(createdGuid.toString())
            .hasActivityLogId(featureId)
            .hasReversePatch(null)
        );
  }

  @Test
  void shouldNotCalculateDiffAfterDeletion() throws Exception {
    // Given
    String featureId = "featureId";
    Guid createdGuid = guid(featureId, new Version(0));
    Guid deletedGuid = guid(featureId, new Version(1));

    // And: space storage that returns features with 'DELETE' and `CREATE` actions
    configureSpaceStorage(
        initialHistoryAwareRequestReturns(List.of(
            nakshaFeature(featureId)
                .withUuid(deletedGuid.toString())
                .withPuuid(createdGuid.toString())
                .withAction(Action.DELETED)
                .withCreatedAt(T0)
                .withUpdatedAt(T1)
                .build()
        )),
        requestForMissingPredecessorsReturns(List.of(
            nakshaFeature("featureId")
                .withUuid(createdGuid.toString())
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
  void shouldFetchSuccessorsDirectlyByVersionIfPossible() {
    // Given
    List<ReadRequest> predecessorRequests = new ArrayList<>();
    Guid puuid = guid("sample_feature", randomVersion());
    configureSpaceStorage(
        initialHistoryAwareRequestReturns(List.of(featureWithPuuidOnly(puuid.toString()))),
        capturingOnlyPredecessorRequest(predecessorRequests)
    );

    // When: handler processes event bearing such request
    handler.processEvent(eventWith(new ReadFeatures()));

    // Then:
    assertEquals(1, predecessorRequests.size());
    ReadFeatures predecessorReq = assertInstanceOf(ReadFeatures.class, predecessorRequests.get(0));
    assertTrue(containsGuuidQuery(predecessorReq, puuid));
    assertFalse(containsNextVersionMetaQuery(predecessorReq));
  }

  @Test
  void shouldFetchSuccessorIndirectlyByNextTnAsFallback() {
    // Given
    List<ReadRequest> predecessorRequests = new ArrayList<>();
    Guid uuid = guid("sample_feature", randomVersion());
    configureSpaceStorage(
        initialHistoryAwareRequestReturns(List.of(featureWithUuidOnly(uuid.toString()))),
        capturingOnlyPredecessorRequest(predecessorRequests)
    );

    // When: handler processes event bearing such request
    handler.processEvent(eventWith(new ReadFeatures()));

    // Then:
    assertEquals(1, predecessorRequests.size());
    ReadFeatures predecessorReq = assertInstanceOf(ReadFeatures.class, predecessorRequests.get(0));
    assertFalse(containsGuuidQuery(predecessorReq));
    assertTrue(containsNextVersionMetaQuery(predecessorReq, uuid.tupleNumber));
  }

  @Test
  void shouldCombineDirectAndIndirectSuccessorsRetrieval() {
    // Given
    List<ReadRequest> predecessorRequests = new ArrayList<>();
    Guid firstFeatureUuid = guid("sample_feature_1", randomVersion());
    Guid secondFeaturePuid = guid("sample_feature_2", randomVersion());
    configureSpaceStorage(
        initialHistoryAwareRequestReturns(List.of(
            featureWithUuidOnly(firstFeatureUuid.toString()),
            featureWithPuuidOnly(secondFeaturePuid.toString())
        )),
        capturingOnlyPredecessorRequest(predecessorRequests)
    );

    // When: handler processes event bearing such request
    handler.processEvent(eventWith(new ReadFeatures()));

    // Then:
    assertEquals(2, predecessorRequests.size());
    assertTrue(predecessorRequests.stream().anyMatch(req -> containsGuuidQuery((ReadFeatures) req, secondFeaturePuid)));
    assertTrue(predecessorRequests.stream().anyMatch(req -> containsNextVersionMetaQuery((ReadFeatures) req, firstFeatureUuid.tupleNumber)));
  }

  private NakshaFeature featureWithPuuidOnly(String puuid) {
    return featureWithXyzFields(Map.of(
        XyzNs.PUUID, puuid
    ));
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
                          && !containsNextVersionMetaQuery((ReadFeatures) readRequest)
                          && !containsGuuidQuery((ReadFeatures) readRequest);
  }

  private ArgumentMatcher<ReadRequest> predecessorRequestMatcher() {
    return readRequest -> isHistoryAwareReadFeatures(readRequest)
                          && (
                              containsNextVersionMetaQuery((ReadFeatures) readRequest)
                              || containsGuuidQuery((ReadFeatures) readRequest)
                          );
  }

  private boolean isHistoryAwareReadFeatures(ReadRequest readRequest) {
    if (readRequest instanceof ReadFeatures rf) {
      return rf.getQueryHistory() && rf.getCollectionIds().size() == 1;
    }
    return false;
  }

  private boolean containsGuuidQuery(ReadFeatures readFeatures, Guid... expectedGuids) {
    GuidList guids = readFeatures.getGuids();
    if (expectedGuids.length == 0) {
      return !guids.isEmpty();
    } else {
      return guids.getSize() == expectedGuids.length && guids.containsAll(Arrays.asList(expectedGuids));
    }
  }

  private boolean containsNextVersionMetaQuery(ReadFeatures readFeatures, TupleNumber... expectedNextTns) {
    IMetaQuery metaQuery = readFeatures.getQuery().getMetadata();
    if (metaQuery instanceof MetaQuery mq) {
      boolean basicCheck = mq.getColumn().equals(MetaColumn.nextVersion())
                           && mq.getOp().equals(AnyOp.IS_ANY_OF)
                           && mq.getValue() instanceof AnyList;
      if (basicCheck && expectedNextTns.length > 0) {
        List queryNextTns = ((AnyList) mq.getValue()).asList();
        return queryNextTns.size() == expectedNextTns.length
               && Arrays.stream(expectedNextTns)
                   .map(tn -> tn.toByteArray(TupleNumberVariant.B96))
                   .allMatch(expectedTnAsBytes -> queryNextTns.stream()
                       .anyMatch(queryTnAsBytes -> Arrays.equals(expectedTnAsBytes, (byte[]) queryTnAsBytes))
                   );
      }
      return basicCheck;
    }
    return false;
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
