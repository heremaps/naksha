package com.here.naksha.handler.activitylog;

import static com.here.naksha.handler.activitylog.ActivityLogSuccessResultAssertions.assertThat;
import static com.here.naksha.test.common.assertions.POpAssertion.assertThatOperation;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.AdditionalMatchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.here.naksha.lib.core.IEvent;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.models.XyzError;
import com.here.naksha.lib.core.models.geojson.implementation.EXyzAction;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeatureCollection;
import com.here.naksha.lib.core.models.geojson.implementation.XyzProperties;
import com.here.naksha.lib.core.models.geojson.implementation.namespaces.XyzNamespace;
import com.here.naksha.lib.core.models.naksha.EventHandler;
import com.here.naksha.lib.core.models.naksha.EventTarget;
import com.here.naksha.lib.core.models.naksha.XyzCollection;
import com.here.naksha.lib.core.models.storage.EExecutedOp;
import com.here.naksha.lib.core.models.storage.ErrorResult;
import com.here.naksha.lib.core.models.storage.ListBasedForwardCursor;
import com.here.naksha.lib.core.models.storage.OpType;
import com.here.naksha.lib.core.models.storage.POp;
import com.here.naksha.lib.core.models.storage.POpType;
import com.here.naksha.lib.core.models.storage.PRef;
import com.here.naksha.lib.core.models.storage.ReadCollections;
import com.here.naksha.lib.core.models.storage.ReadFeatures;
import com.here.naksha.lib.core.models.storage.ReadRequest;
import com.here.naksha.lib.core.models.storage.Request;
import com.here.naksha.lib.core.models.storage.Result;
import com.here.naksha.lib.core.models.storage.SuccessResult;
import com.here.naksha.lib.core.models.storage.WriteXyzCollections;
import com.here.naksha.lib.core.models.storage.WriteXyzFeatures;
import com.here.naksha.lib.core.models.storage.XyzFeatureCodec;
import com.here.naksha.lib.core.models.storage.XyzFeatureCodecFactory;
import com.here.naksha.lib.core.storage.IReadSession;
import com.here.naksha.lib.core.storage.IStorage;
import com.here.naksha.lib.core.util.json.JsonSerializable;
import com.here.naksha.test.common.FileUtil;
import com.here.naksha.test.common.assertions.POpAssertion;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

class ActivityLogHandlerTest {

  private static final String SPACE_ID = "test_activity_space";

  @Mock
  INaksha naksha;

  @Mock
  EventHandler eventHandler;

  @Mock
  IStorage spaceStorage;

  private ActivityLogHandler handler;

  @BeforeEach
  void setup() {
    MockitoAnnotations.openMocks(this);
    when(naksha.getSpaceStorage()).thenReturn(spaceStorage);
    when(eventHandler.getProperties()).thenReturn(new ActivityLogHandlerProperties(SPACE_ID));
    handler = new ActivityLogHandler(eventHandler, naksha, mock(EventTarget.class));
  }

  @ParameterizedTest
  @MethodSource("unhandledRequests")
  void shouldFailOnNonReadFeaturesRequests(Request<?> unhandledRequest) {
    // Given:
    IEvent event = eventWith(unhandledRequest);

    // When:
    Result result = handler.processEvent(event);

    // Then: Storage was not used at all
    verifyNoInteractions(spaceStorage);

    // And: Error result (NOT_IMPLEMENTED) was returned
    assertInstanceOf(ErrorResult.class, result);
    assertEquals(XyzError.NOT_IMPLEMENTED, ((ErrorResult) result).reason);
  }

  @Test
  void shouldImmediatelySendWriteCollectionUpstream() {
    // Given:
    IEvent event = eventWith(new WriteXyzCollections().delete(new XyzCollection("some_collection")));

    // When:
    handler.processEvent(event);

    // Then: Storage was not used at all
    verify(event).sendUpstream();
  }

  @Test
  void shouldTransformReadRequest() throws Exception {
    // Given: Original read request
    String featureId = "featureId";
    String activityLogId = "activityLogId";
    ReadFeatures originalReadFeatures = new ReadFeatures("not_the_space_id")
        .withReturnAllVersions(false)
        .withPropertyOp(
            POp.or(
                POp.eq(PRef.id(), featureId),
                POp.eq(PRef.activityLogId(), activityLogId)
            )
        );

    // And: Configured session that will receive read request from handler
    IReadSession readSession = mock(IReadSession.class);
    when(spaceStorage.newReadSession(any(), anyBoolean())).thenReturn(readSession);
    when(readSession.execute(any())).thenReturn(new SuccessResult());

    // When: Processing event with original request
    handler.processEvent(eventWith(originalReadFeatures));

    // Then: Some request was executed by the session
    ArgumentCaptor<ReadFeatures> requestCaptor = ArgumentCaptor.forClass(ReadFeatures.class);
    verify(readSession).execute(requestCaptor.capture());

    // And: The executed request was a ReadFeatures transformed by handler
    ReadFeatures requestPassedToSpaceStorage = requestCaptor.getValue();
    assertEquals(List.of(SPACE_ID), requestPassedToSpaceStorage.getCollections(),
        "Transformed request should use 'spaceId' from handler's properties");
    assertTrue(requestPassedToSpaceStorage.isReturnAllVersions(), "Transformed request should return all versions of feature");
    assertThatOperation(requestPassedToSpaceStorage.getPropertyOp()) // POp for id and activityLogId should be transformed
        .hasChildrenThat(
            first -> first
                .hasType(POpType.EQ)
                .hasPRef(PRef.uuid()),
            second -> second
                .hasType(POpType.EQ)
                .hasPRef(PRef.id())
        );
  }

  @Test
  void shouldComposeActivityFeatures() throws Exception {
    // Given
    String featureId = "featureId";
    XyzFeature oldFeature = xyzFeature(
        featureId,
        "initial_uuid",
        null,
        EXyzAction.CREATE,
        Map.of(
            "name", "old feature",
            "magicNumber", 123
        )
    );
    XyzFeature newFeature = xyzFeature(
        featureId,
        "new_uuid",
        "initial_uuid",
        EXyzAction.UPDATE,
        Map.of(
            "name", "new feature",
            "magicBoolean", true
        )
    );

    // And
    ReadFeatures request = new ReadFeatures();
    spaceStorageSessionReturningHistoryFeatures(request, oldFeature, newFeature);

    // When
    Result result = handler.processEvent(eventWith(request));

    // Then:
    assertThat(result)
        .hasActivityFeatures(
            firstFeature -> firstFeature
                .hasId(newFeature.getProperties().getXyzNamespace().getUuid())
                .hasActivityLogId(featureId)
                .hasAction(EXyzAction.UPDATE.toString())
                .hasReversePatch(jsonNode("""
                    {
                      "insert": 1,
                      "remove": 1,
                      "update": 1,
                      "ops": [
                        {
                          "name": "replace",
                          "path": "properties/name",
                          "value": "old feature"
                        },
                        {
                          "name": "add",
                          "path": "properties/magicNumber",
                          "value": 123
                        },
                        {
                          "name": "remove",
                          "path": "properties/magicBoolean",
                          "value": null
                        }
                      ]
                    }
                    """)),
            secondFeature -> secondFeature
                .hasId(oldFeature.getProperties().getXyzNamespace().getUuid())
                .hasActivityLogId(featureId)
                .hasAction(EXyzAction.CREATE.toString())
                .hasReversePatch(null)
        );
  }

  @Test
  void shouldFetchAdditionalHistoryFeaturesWhenNeeded() throws Exception {
    // Given: Client request (we don't care about it's specifics)
    ReadFeatures firstRequest = new ReadFeatures();

    // And: Space storage that will return two history features for client's request
    IReadSession readSession = spaceStorageSessionReturningHistoryFeatures(firstRequest,
        xyzFeature("id_1", "uuid_1", "puuid_1", EXyzAction.UPDATE),
        xyzFeature("id_2", "uuid_2", "puuid_2", EXyzAction.DELETE)
    );

    // And: Space storage that will return two predecessors for any other request
    when(readSession.execute(not(eq(firstRequest)))).thenReturn(new SuccessHistoryResult(List.of(
        xyzFeature("id_1", "puuid_1", null, EXyzAction.CREATE),
        xyzFeature("id_2", "puuid_2", null, EXyzAction.CREATE)
    )));

    // When: Handler processes event with original client's request
    Result result = handler.processEvent(eventWith(firstRequest));

    // Then: Space storage should be queried twice
    ArgumentCaptor<ReadFeatures> requestCaptor = ArgumentCaptor.forClass(ReadFeatures.class);
    verify(readSession, times(2)).execute(requestCaptor.capture());

    // And: First request passed to the space should be the client one
    List<ReadFeatures> requestPassedToSpace = requestCaptor.getAllValues();
    assertEquals(2, requestPassedToSpace.size());
    assertEquals(firstRequest, requestPassedToSpace.get(0));

    // And: Second request passed to space should be about fetching additional predecessors
    ReadFeatures secondRequest = requestPassedToSpace.get(1);
    assertTrue(secondRequest.isReturnAllVersions());
    assertEquals(List.of(SPACE_ID), secondRequest.getCollections());
    POpAssertion.assertThatOperation(secondRequest.getPropertyOp())
        .hasType(OpType.OR)
        .hasChildrenThat(
            first -> first
                .hasPRef(PRef.uuid())
                .hasType(POpType.EQ)
                .hasValue("puuid_2"),
            second -> second
                .hasPRef(PRef.uuid())
                .hasType(POpType.EQ)
                .hasValue("puuid_1")
        );

    // And: Handler's result should only contain features from the first response (to client's request)
    assertThat(result)
        .hasActivityFeatures(
            first -> first
                .hasId("uuid_2")
                .hasActivityLogId("id_2")
                .hasAction(EXyzAction.DELETE.toString()),
            second -> second
                .hasId("uuid_1")
                .hasActivityLogId("id_1")
                .hasAction(EXyzAction.UPDATE.toString())
        );
  }

  @Test
  void shouldNotCalculateReversePatchAfterCreation() throws Exception {
    // Given:
    ReadFeatures request = new ReadFeatures();

    // And
    spaceStorageSessionReturningHistoryFeatures(request, xyzFeature(
        "featureId",
        "uuid",
        null,
        EXyzAction.CREATE
    ));

    // When:
    Result result = handler.processEvent(eventWith(request));

    // Then:
    assertThat(result)
        .hasActivityFeatures(feature -> feature
            .hasAction(EXyzAction.CREATE.toString())
            .hasId("uuid")
            .hasActivityLogId("featureId")
            .hasReversePatch(null)
        );
  }

  @Test
  void shouldNotCalculateDiffAfterDeletion() throws Exception {
    // Given:
    ReadFeatures request = new ReadFeatures();

    // And
    spaceStorageSessionReturningHistoryFeatures(request,
        xyzFeature(
            "featureId",
            "delete_uuid",
            "create_uuid",
            EXyzAction.DELETE
        ),
        xyzFeature(
            "featureId",
            "create_uuid",
            null,
            EXyzAction.CREATE
        )
    );

    // When:
    Result result = handler.processEvent(eventWith(request));

    // Then:
    assertThat(result)
        .hasActivityFeatures(
            first -> first
                .hasAction(EXyzAction.DELETE.toString())
                .hasId("delete_uuid")
                .hasActivityLogId("featureId")
                .hasReversePatch(null),
            second -> second
                .hasAction(EXyzAction.CREATE.toString())
                .hasId("create_uuid")
                .hasActivityLogId("featureId")
                .hasReversePatch(null)
        );
  }

  private static JsonNode jsonNode(String rawJson) {
    try {
      return new ObjectMapper().readTree(rawJson);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  //TODO: is it even possible to correctly prepare this test? save for later
//  @ParameterizedTest
//  @MethodSource("datahubSamples")
  void shouldBeAlignedWithDataHubSamples(DatahubSample datahubSample) {
    // Given:
    ReadFeatures request = new ReadFeatures();

    // And:
    spaceStorageSessionReturningHistoryFeatures(request, datahubSample.historyFeatures);

    // And
    IEvent event = eventWith(request);

    // When
    Result result = handler.processEvent(event);

    // Then
    assertInstanceOf(ActivityLogSuccessResult.class, result);
  }

  private static XyzFeature xyzFeature(String id, String uuid, String puuid, EXyzAction action) {
    return xyzFeature(id, uuid, puuid, action, Collections.emptyMap());
  }

  private static XyzFeature xyzFeature(String id, String uuid, String puuid, EXyzAction action, Map properties) {
    XyzFeature feature = new XyzFeature(id);
    XyzNamespace xyzNamespace = new XyzNamespace();
    xyzNamespace.setUuid(uuid);
    xyzNamespace.setPuuid(puuid);
    xyzNamespace.setAction(action);
    XyzProperties xyzProperties = new XyzProperties();
    xyzProperties.putAll(properties);
    xyzProperties.setXyzNamespace(xyzNamespace);
    feature.setProperties(xyzProperties);
    return feature;
  }

  private static Stream<Arguments> datahubSamples() {
    return Stream.of(1)
        .map(sampleId -> arguments(DatahubSample.sampleForId(sampleId)));
  }

  IReadSession spaceStorageSessionReturningHistoryFeatures(ReadRequest<?> handledRequest, XyzFeature... historyFeatures) {
    return spaceStorageSessionReturningHistoryFeatures(handledRequest, List.of(historyFeatures));
  }

  IReadSession spaceStorageSessionReturningHistoryFeatures(ReadRequest<?> handledRequest, List<XyzFeature> historyFeatures) {
    IReadSession readSession = mock(IReadSession.class);
    when(readSession.execute(handledRequest)).thenReturn(new SuccessHistoryResult(historyFeatures));
    when(spaceStorage.newReadSession(any(), anyBoolean())).thenReturn(readSession);
    return readSession;
  }

  private IEvent eventWith(Request request) {
    IEvent event = Mockito.mock(IEvent.class);
    when(event.getRequest()).thenReturn(request);
    return event;
  }

  private static Stream<Request<?>> unhandledRequests() {
    return Stream.of(
        new WriteXyzFeatures("some_collection").create(new XyzFeature("some_feature")),
        new ReadCollections().withIds("some_collection")
    );
  }

  private static class SuccessHistoryResult extends SuccessResult {

    private SuccessHistoryResult(List<XyzFeature> historyFeatures) {
      this.cursor = featureListCursor(historyFeatures);
    }

    private static ListBasedForwardCursor<XyzFeature, XyzFeatureCodec> featureListCursor(List<XyzFeature> features) {
      XyzFeatureCodecFactory codecFactory = XyzFeatureCodecFactory.get();
      List<XyzFeatureCodec> codecs = features.stream()
          .map(feature -> codecFactory
              .newInstance()
              .withOp(EExecutedOp.READ)
              .withFeature(feature)
          )
          .toList();
      return new ListBasedForwardCursor<>(codecFactory, codecs);
    }
  }

  private record DatahubSample(
      String sampleDir,
      List<XyzFeature> historyFeatures,
      List<XyzFeature> activityLogFeatures
  ) {

    private static final String DATAHUB_SAMPLES_DIR = "src/test/resources/dh_samples";
    private static final String SAMPLE_HISTORY_JSON = "history_features.json";
    private static final String SAMPLE_ACTIVITY_LOG_JSON = "activity_log_features.json";

    @Override
    public String toString() {
      return "Datahub sample from dir: " + sampleDir;
    }

    static DatahubSample sampleForId(int sampleId) {
      String sampleDir = "%s/%d/".formatted(DATAHUB_SAMPLES_DIR, sampleId);
      String historyJson = FileUtil.loadFileOrFail(sampleDir, SAMPLE_HISTORY_JSON);
      String activityHistoryJson = FileUtil.loadFileOrFail(sampleDir, SAMPLE_ACTIVITY_LOG_JSON);
      return new DatahubSample(
          sampleDir,
          featuresFromCollectionJson(historyJson),
          featuresFromCollectionJson(activityHistoryJson)
      );
    }

    private static List<XyzFeature> featuresFromCollectionJson(String featuresCollectionJson) {
      XyzFeatureCollection collection = JsonSerializable.deserialize(featuresCollectionJson, XyzFeatureCollection.class);
      return collection.getFeatures();
    }
  }
}