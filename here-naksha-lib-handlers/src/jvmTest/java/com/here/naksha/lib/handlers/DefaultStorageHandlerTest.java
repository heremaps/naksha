package com.here.naksha.lib.handlers;

import static naksha.model.NakshaError.COLLECTION_NOT_FOUND;
import static naksha.model.NakshaError.MAP_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Named.named;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.here.naksha.lib.core.IEvent;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.models.naksha.EventHandlerConfig;
import com.here.naksha.lib.core.models.naksha.Space;
import com.here.naksha.lib.core.models.naksha.SpaceProperties;
import com.here.naksha.lib.handlers.DefaultStorageHandlerTest.CollectionPriorityTestCase.ValidCollectionSource;
import com.here.naksha.lib.handlers.util.RequestTypesUtil;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import naksha.base.JvmBoxingUtil;
import naksha.base.StringList;
import naksha.base.fn.Fn1;
import naksha.base.fn.Fx1;
import naksha.model.IReadSession;
import naksha.model.IStorage;
import naksha.model.IWriteSession;
import naksha.model.Naksha;
import naksha.model.NakshaContext;
import naksha.model.NakshaError;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaCollection;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaProperties;
import naksha.model.objects.NakshaStorage;
import naksha.model.request.ErrorResponse;
import naksha.model.request.ReadFeatures;
import naksha.model.request.Request;
import naksha.model.request.Response;
import naksha.model.request.SuccessResponse;
import naksha.model.request.Write;
import naksha.model.request.WriteList;
import naksha.model.request.WriteOp;
import naksha.model.request.WriteRequest;
import org.apache.commons.lang3.RandomUtils;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class DefaultStorageHandlerTest extends AbstractTest {

  public DefaultStorageHandlerTest() {
    super("default_storage_handler_test_context", "default_storage_handler_test_map");
  }

  private static final String HANDLER_ID = "test_handler";
  private static final String STORAGE_ID = "dsh_test_storage_id";
  private static final Logger log = LoggerFactory.getLogger(DefaultStorageHandlerTest.class);

  @Mock
  INaksha naksha;

  @Mock
  IStorage storage;

  @Mock
  IWriteSession storageWriteSession;

  @Mock
  IReadSession storageReadSession;

  @BeforeEach
  void setup() {
    MockitoAnnotations.openMocks(this);
    when(storage.getId()).thenReturn(STORAGE_ID);
    configureStorageSessionMocks();
    configureStorageConfig(storageConfigWithMapId("test_map_id"));
  }

  @Test
  void shouldFailWhenStorageIdIsUndefined() {
    // Given: handler with undefined storageId
    DefaultStorageHandler handler = storageHandler(handlerPropertiesWithoutStorageId());

    // When: processing random event
    Response result = handler.processEvent(event(writeRandomFeature()));

    // Then: result is NOT_FOUND due to missing storageId
    assertInstanceOf(ErrorResponse.class, result);
    assertEquals(NakshaError.NOT_FOUND, ((ErrorResponse) result).getError().getCode());
    assertEquals("No storageId configured for handler.", ((ErrorResponse) result).getError().getMsg());
  }

  @ParameterizedTest
  @MethodSource("collectionPriorityTestCases")
  void shouldApplyCorrectCollection(CollectionPriorityTestCase testCase) {
    // Given: Always succeeding storage writer
    when(storageWriteSession.execute(any(WriteRequest.class))).thenReturn(new SuccessResponse());

    // And: feature to be saved in potentially different collection
    NakshaFeature featureToCreate = new NakshaFeature("sample_feature");
    WriteRequest writeXyzFeatures = new WriteRequest().add(
        new Write().createFeature("different_map", "different_collection", featureToCreate));

    // And: Handler to test
    DefaultStorageHandler handler = storageHandler(testCase.handlerProperties, testCase.space);

    // When: Processing write features
    Response result = handler.processEvent(event(writeXyzFeatures));

    // Then: Write features request was passed to storage writer
    ArgumentCaptor<WriteRequest> storageWriterRequestCaptor = ArgumentCaptor.forClass(WriteRequest.class);
    verify(storageWriteSession).execute(storageWriterRequestCaptor.capture());

    // And: Request executed by storage has CollectionId chosen by priority
    WriteRequest requestPassedToStorageWriter = storageWriterRequestCaptor.getValue();
    assertEquals(testCase.correctCollection().getId(), requestPassedToStorageWriter.getWrites().get(0).getCollectionId());

    // And: The rest of request's payload remained the same
    assertEquals(1, requestPassedToStorageWriter.getWrites().size());
    assertEquals(featureToCreate, requestPassedToStorageWriter.getWrites().get(0).getFeature());

    // And: Operation succeeded
    assertInstanceOf(SuccessResponse.class, result);
  }

  @ParameterizedTest
  @MethodSource("collectionPriorityTestCases")
  void shouldCreateMissingCollectionRespectingPriority(CollectionPriorityTestCase testCase) {
    // Given: Storage writer failing on WriteRequest for features due to undefined table but is able to create new collection
    NakshaError missingCollectionError = new NakshaError(COLLECTION_NOT_FOUND, "Missing collection");
    when(
        storageWriteSession.execute(argThat(DefaultStorageHandlerTest::isOnlyWriteFeaturesRequest)))
        .thenReturn(new ErrorResponse(missingCollectionError));
    when(storageWriteSession.execute(
        argThat(DefaultStorageHandlerTest::isOnlyWriteCollectionsRequest)))
        .thenReturn(new SuccessResponse());

    // And: feature to be saved in potentially different collection
    NakshaFeature featureToCreate = new NakshaFeature("sample_feature");
    WriteRequest writeXyzFeatures = new WriteRequest()
        .add(new Write().createFeature("different_collection", featureToCreate));

    // And: Handler with autoCreateCollection enabled to test
    DefaultStorageHandler handler = storageHandler(testCase.handlerProperties, testCase.space);
    assertTrue(handler.properties.getAutoCreateCollection());

    // When: Processing write features
    ignoreExceptionsFrom(
        () -> handler.processEvent(event(writeXyzFeatures)),
        "The mock for storage writer is already configured to always fail - it's ok to allow this as we only want to check invocations"
    );

    // Then: We got 3 Write Requests (write feature - failed, write collection - success, write feature - retried)
    ArgumentCaptor<WriteRequest> storageWriterRequestCaptor = ArgumentCaptor.forClass(WriteRequest.class);
    verify(storageWriteSession, times(3)).execute(storageWriterRequestCaptor.capture());
    List<WriteRequest> capturedWrites = storageWriterRequestCaptor.getAllValues();

    // And: passed Write Collection request was about creating single collection with correct id
    Write writeCollection = findSingleCreateCollectionWrite(capturedWrites);
    assertEquals(WriteOp.CREATE, writeCollection.getOp());
    assertEquals(testCase.correctCollection().getId(), writeCollection.getId());
    assertEquals(Naksha.COLLECTIONS_COL, writeCollection.getCollectionId());

    // And: write features related to the same feature in correct collection
    List<Write> featureWrites = getSingularWritesToCollection(capturedWrites, testCase.correctCollection().getId());
    assertEquals(2, featureWrites.size());
    for (Write writeFeature : featureWrites) {
      assertEquals(WriteOp.CREATE, writeFeature.getOp());
      assertEquals(featureToCreate.getId(), writeFeature.getId());
      assertEquals(testCase.correctCollection().getId(), writeFeature.getCollectionId());
    }
  }

  @Test
  void shouldCreateMissingCollection() {
    // Given: Storage writer failing on WriteXyzFeatures due to sql exception
    NakshaError missingCollectionError = new NakshaError(COLLECTION_NOT_FOUND, "Missing collection");
    when(storageWriteSession.execute(any(WriteRequest.class))).thenReturn(new ErrorResponse(missingCollectionError));

    // And: Handler with autoCreateCollection enabled to test
    DefaultStorageHandler handler = storageHandler();
    assertTrue(handler.properties.getAutoCreateCollection());

    // And: feature to be saved in potentially different collection
    NakshaFeature featureToCreate = new NakshaFeature("sample_feature");
    String collectionId = handler.properties.getCollection().getId();
    WriteRequest writeXyzFeatures = new WriteRequest().add(new Write().createFeature(collectionId, featureToCreate));

    // When: Processing write features
    ignoreExceptionsFrom(
        () -> handler.processEvent(event(writeXyzFeatures)),
        "The mock for storage writer is already configured to always fail - it's ok to allow this as we only want to check invocations"
    );

    // Then: We got two Write Requests in total (creating feature & create collection)
    ArgumentCaptor<WriteRequest> storageWriterRequestCaptor = ArgumentCaptor.forClass(WriteRequest.class);
    verify(storageWriteSession, times(2)).execute(storageWriterRequestCaptor.capture());
    List<WriteRequest> capturedWriteRequests = storageWriterRequestCaptor.getAllValues();
    List<Write> capturedFeatureWrites = getSingularWritesToCollection(capturedWriteRequests, collectionId);
    Write capturedCollectionWrite = findSingleCreateCollectionWrite(capturedWriteRequests);
    assertEquals(1, capturedFeatureWrites.size(), "Expected single feature write");
    assertNotNull(capturedCollectionWrite, "Could not capture writing collection");

    // And: passed Write Collection request was about creating collection defined in Handler properties
    assertEquals(WriteOp.CREATE, capturedCollectionWrite.getOp());
    assertEquals(handler.properties.getCollection().getId(), capturedCollectionWrite.getId());
    assertHubSlimIndices(collectionFrom(capturedCollectionWrite));
    assertNull(handler.properties.getCollection().getIndices(), "Handler collection config must not be mutated");
  }

  @Test
  void shouldPreserveExplicitEmptyIndicesWhenCreatingMissingCollection() {
    // Given: Storage writer failing on feature writes due to missing collection and accepting collection create
    NakshaError missingCollectionError = new NakshaError(COLLECTION_NOT_FOUND, "Missing collection");
    when(storageWriteSession.execute(argThat(DefaultStorageHandlerTest::isOnlyWriteFeaturesRequest)))
        .thenReturn(new ErrorResponse(missingCollectionError));
    when(storageWriteSession.execute(argThat(DefaultStorageHandlerTest::isOnlyWriteCollectionsRequest)))
        .thenReturn(new SuccessResponse());

    // And: Handler collection config explicitly disables optional indices
    DefaultStorageHandlerProperties handlerProperties = handlerProperties();
    handlerProperties.getCollection().setIndices(new StringList());
    DefaultStorageHandler handler = storageHandler(handlerProperties);

    // When
    ignoreExceptionsFrom(
        () -> handler.processEvent(event(writeRandomFeature())),
        "The mock for storage writer is configured to fail feature writes after collection creation"
    );

    // Then
    ArgumentCaptor<WriteRequest> storageWriterRequestCaptor = ArgumentCaptor.forClass(WriteRequest.class);
    verify(storageWriteSession, times(3)).execute(storageWriterRequestCaptor.capture());
    NakshaCollection createdCollection = collectionFrom(findSingleCreateCollectionWrite(storageWriterRequestCaptor.getAllValues()));
    assertNotNull(createdCollection.getIndices());
    assertEquals(0, createdCollection.getIndices().size());
    assertEquals(0, handlerProperties.getCollection().getIndices().size(), "Handler collection config must not be mutated");
  }

  @Test
  void shouldPreserveCustomIndicesWhenCreatingMissingCollection() {
    // Given: Storage writer failing on feature writes due to missing collection and accepting collection create
    NakshaError missingCollectionError = new NakshaError(COLLECTION_NOT_FOUND, "Missing collection");
    when(storageWriteSession.execute(argThat(DefaultStorageHandlerTest::isOnlyWriteFeaturesRequest)))
        .thenReturn(new ErrorResponse(missingCollectionError));
    when(storageWriteSession.execute(argThat(DefaultStorageHandlerTest::isOnlyWriteCollectionsRequest)))
        .thenReturn(new SuccessResponse());

    // And: Handler collection config explicitly requests custom indices
    DefaultStorageHandlerProperties handlerProperties = handlerProperties();
    handlerProperties.getCollection().setIndices(StringList.of("tags"));
    DefaultStorageHandler handler = storageHandler(handlerProperties);

    // When
    ignoreExceptionsFrom(
        () -> handler.processEvent(event(writeRandomFeature())),
        "The mock for storage writer is configured to fail feature writes after collection creation"
    );

    // Then
    ArgumentCaptor<WriteRequest> storageWriterRequestCaptor = ArgumentCaptor.forClass(WriteRequest.class);
    verify(storageWriteSession, times(3)).execute(storageWriterRequestCaptor.capture());
    NakshaCollection createdCollection = collectionFrom(findSingleCreateCollectionWrite(storageWriterRequestCaptor.getAllValues()));
    assertIndices(createdCollection, "tags");
    assertIndices(handlerProperties.getCollection(), "tags");
  }

  @Test
  void shouldNotCreateCollectionWhenAutoCreateIsDisabled() {
    // Given: Storage writer failing on WriteXyzFeatures due to missing collection exception
    NakshaError missingCollectionError = new NakshaError(COLLECTION_NOT_FOUND, "Missing collection");
    when(storageWriteSession.execute(any(WriteRequest.class))).thenReturn(new ErrorResponse(missingCollectionError));

    // And: feature to be saved in potentially different collection
    NakshaFeature featureToCreate = new NakshaFeature("sample_feature");
    WriteRequest writeXyzFeatures = new WriteRequest().add(new Write().createFeature("different_collection", featureToCreate));

    // And: Handler with autoCreateCollection disabled to test
    DefaultStorageHandler handler = storageHandler();
    handler.properties.setAutoCreateCollection(false);

    // When: Processing write features
    ignoreExceptionsFrom(
        () -> handler.processEvent(event(writeXyzFeatures)),
        "The mock for storage writer is already configured to always fail - it's ok to allow this as we only want to check invocations"
    );

    // Then: No Write Collection request was passed to storage writer
    verify(storageWriteSession, never()).execute(argThat(matchesCreateCollectionRequest()));
  }

  @Test
  void shouldFailWhenStorageHasNoConfig() {
    // Given:
    configureStorageConfig(null);

    // And
    DefaultStorageHandler handler = storageHandler();

    // When:
    Response response = handler.process(event(writeRandomFeature()));

    // Then:
    assertInstanceOf(ErrorResponse.class, response);
    ErrorResponse errorResponse = (ErrorResponse) response;
    assertEquals(NakshaError.ILLEGAL_STATE, errorResponse.getError().getCode());
    assertEquals(
        "Unable to determine 'mapId' for handler '" + HANDLER_ID + "', storage '" + STORAGE_ID + "' has no config.",
        errorResponse.getError().getMsg()
    );
  }

  @Test
  void shouldSucceedWhenStorageConfigHasNoMapId() {
    // Given:
    configureStorageConfig(new NakshaStorage());

    // And
    DefaultStorageHandler handler = storageHandler();

    // And
    when(storageWriteSession.execute(any(WriteRequest.class))).thenReturn(new SuccessResponse());

    // When:
    Response response = handler.process(event(writeRandomFeature()));

    // Then:
    assertInstanceOf(SuccessResponse.class, response);
  }

  @Test
  void shouldCreateMapIfMissing() {
    // Given:
    String mapId = "missing_map_id";
    configureStorageConfig(storageConfigWithMapId(mapId));

    // And: Storage writer failing on WriteXyzFeatures due to sql exception
    NakshaError missingMapError = new NakshaError(MAP_NOT_FOUND, "Missing map");
    when(storageWriteSession.execute(any(WriteRequest.class))).thenReturn(new ErrorResponse(missingMapError));

    // And
    DefaultStorageHandler handler = storageHandler();

    // When: Processing write features
    Request writeFeatureReq = writeRandomFeature();
    ignoreExceptionsFrom(
        () -> handler.processEvent(event(writeFeatureReq)),
        "The mock for storage writer is already configured to always fail - it's ok to allow this as we only want to check invocations"
    );

    // Then:
    ArgumentCaptor<WriteRequest> storageWriterRequestCaptor = ArgumentCaptor.forClass(WriteRequest.class);
    verify(storageWriteSession, times(2)).execute(storageWriterRequestCaptor.capture());
    List<WriteRequest> capturedWriteRequests = storageWriterRequestCaptor.getAllValues();
    assertEquals(writeFeatureReq, capturedWriteRequests.get(0));

    // And:
    List<Write> secondRequestWrites = capturedWriteRequests.get(1).getWrites();
    assertEquals(1, secondRequestWrites.size());
    Write mapWrite = secondRequestWrites.get(0);
    assertEquals(WriteOp.CREATE, mapWrite.getOp());
    assertEquals(Naksha.ADMIN_MAP, mapWrite.getMapId());
    assertEquals(Naksha.MAPS_COL, mapWrite.getCollectionId());
    assertEquals(mapId, mapWrite.getId());
  }

  @Test
  void shouldUseMapFromStorageProps() {
    // Given:
    String mapIdFromStorageProps = "map_from_storage";
    configureStorageConfig(storageConfigWithMapId(mapIdFromStorageProps));

    // And: Storage writer failing on WriteXyzFeatures due to sql exception
    NakshaFeature featureToCreate = new NakshaFeature("sample_feature");
    WriteRequest writeRequest = new WriteRequest();
    writeRequest.add(new Write().createFeature("map_from_request", "map_from_collection", featureToCreate));

    // And
    DefaultStorageHandler handler = storageHandler();

    // When: Processing write features
    ignoreExceptionsFrom(
        () -> handler.processEvent(event(writeRequest)),
        "The mock for storage writer is already configured to always fail - it's ok to allow this as we only want to check invocations"
    );

    // Then:
    ArgumentCaptor<WriteRequest> storageWriterRequestCaptor = ArgumentCaptor.forClass(WriteRequest.class);
    verify(storageWriteSession, times(1)).execute(storageWriterRequestCaptor.capture());
    WriteRequest capturedWriteRequest = storageWriterRequestCaptor.getValue();
    List<Write> subsmittedWrites = capturedWriteRequest.getWrites();
    assertEquals(1, subsmittedWrites.size());
    Write submittedWrite = subsmittedWrites.get(0);
    assertEquals(WriteOp.CREATE, submittedWrite.getOp());
    assertEquals(mapIdFromStorageProps, submittedWrite.getMapId());
    assertEquals(handler.properties.getCollection().getId(), submittedWrite.getCollectionId());
  }

  @Test
  void shouldReattemptWriteCollectionsAfterMissingMapByCreatingMap() {
    // Given
    final String mapIdFromStorageProps = "map_from_storage_props";
    configureStorageConfig(storageConfigWithMapId(mapIdFromStorageProps));

    // And: First attempt of WriteCollections -> MAP_NOT_FOUND, map creation -> success, second attempt -> success
    NakshaError missingMapError = new NakshaError(MAP_NOT_FOUND, "Missing map");
    when(storageWriteSession.execute(argThat(RequestTypesUtil::isOnlyWriteCollections)))
        .thenReturn(new ErrorResponse(missingMapError))
        .thenReturn(new SuccessResponse());
    when(storageWriteSession.execute(argThat(matchesCreateMapRequest(mapIdFromStorageProps))))
        .thenReturn(new SuccessResponse());

    // And: We get DefaultStorageHandler without collection in handler properties but event target as space with collectionId
    DefaultStorageHandlerProperties hp = handlerProperties("storageId");
    hp.setCollection(null);
    DefaultStorageHandler handler = storageHandler(hp, space("spaceId", spacePropertiesWithCollection("target_collection")));

    WriteRequest updateColReq = updateCollectionRequest("target_collection");

    // When
    Response response = handler.processEvent(event(updateColReq));

    // Then
    assertInstanceOf(SuccessResponse.class, response);

    // We expect: 1) original WriteCollections (fails), 2) create map (success), 3) original WriteCollections (success)
    ArgumentCaptor<WriteRequest> captor = ArgumentCaptor.forClass(WriteRequest.class);
    verify(storageWriteSession, times(3)).execute(captor.capture());
    List<WriteRequest> calls = captor.getAllValues();

    // 1st and 3rd are WriteCollections against COLLECTIONS_COL with map from storage props
    assertTrue(RequestTypesUtil.isOnlyWriteCollections(calls.get(0)));
    assertTrue(RequestTypesUtil.isOnlyWriteCollections(calls.get(2)));
    assertEquals(Naksha.COLLECTIONS_COL, calls.get(0).getWrites().get(0).getCollectionId());
    assertEquals(mapIdFromStorageProps, calls.get(0).getWrites().get(0).getMapId());
    assertEquals("target_collection", calls.get(0).getWrites().get(0).getFeature().getId());
    assertEquals(Naksha.COLLECTIONS_COL, calls.get(2).getWrites().get(0).getCollectionId());
    assertEquals(mapIdFromStorageProps, calls.get(2).getWrites().get(0).getMapId());
    assertEquals("target_collection", calls.get(2).getWrites().get(0).getFeature().getId());

    // 2nd call is map creation in admin map / maps collection
    Write mapCreate = calls.get(1).getWrites().get(0);
    assertEquals(WriteOp.CREATE, mapCreate.getOp());
    assertEquals(Naksha.ADMIN_MAP, mapCreate.getMapId());
    assertEquals(Naksha.MAPS_COL, mapCreate.getCollectionId());
    assertEquals(mapIdFromStorageProps, mapCreate.getId());
  }


  @Test
  void shouldReturnSuccessWithoutActionWhenUpdateCollectionAndAutoCreateDisabled() {
    // Given
    DefaultStorageHandler handler = storageHandler();
    handler.properties.setAutoCreateCollection(false);

    // When
    Response response = handler.processEvent(event(updateCollectionRequest("no_op_update_col")));

    // Then
    assertInstanceOf(SuccessResponse.class, response);
    // No storage write should be performed
    verify(storageWriteSession, never()).execute(any(WriteRequest.class));
  }

  @Test
  void shouldReturnSuccessWithoutActionWhenDeleteCollectionAndAutoDeleteDisabled() {
    // Given
    DefaultStorageHandler handler = storageHandler();
    handler.properties.setAutoDeleteCollection(false);

    // When
    Response response = handler.processEvent(event(deleteCollectionRequest("no_op_delete_col")));

    // Then
    assertInstanceOf(SuccessResponse.class, response);
    // No storage write should be performed
    verify(storageWriteSession, never()).execute(any(WriteRequest.class));
  }

  @Test
  void shouldApplyMapIdAndCollectionsColForWriteCollections() {
    // Given
    final String mapIdFromStorageProps = "schema_for_col_ops";
    configureStorageConfig(storageConfigWithMapId(mapIdFromStorageProps));
    when(storageWriteSession.execute(any(WriteRequest.class))).thenReturn(new SuccessResponse());

    // And: We get DefaultStorageHandler without collection in handler properties but event target as space with collectionId
    DefaultStorageHandlerProperties hp = handlerProperties("storageId");
    hp.setCollection(null);
    DefaultStorageHandler handler = storageHandler(hp, space("spaceId", spacePropertiesWithCollection("apply_col")));

    // When
    Response response = handler.processEvent(event(updateCollectionRequest("apply_col")));

    // Then
    assertInstanceOf(SuccessResponse.class, response);

    ArgumentCaptor<WriteRequest> captor = ArgumentCaptor.forClass(WriteRequest.class);
    verify(storageWriteSession).execute(captor.capture());
    Write write = captor.getValue().getWrites().get(0);
    assertTrue(RequestTypesUtil.isOnlyWriteCollections(captor.getValue()));
    assertEquals(Naksha.COLLECTIONS_COL, write.getCollectionId(), "WriteCollections must target naksa~collections collection");
    assertEquals(mapIdFromStorageProps, write.getMapId(), "MapId must be taken from storage props");
    assertEquals("apply_col", write.getFeature().getId());
  }

  @Test
  void shouldRespectCustomStoragePropertiesOnWrite() {
    // Given
    int socketTimeoutSec = 2;
    int connectionTimeoutSec = 4;
    int stmtTimeoutSec = 7;

    // And
    NakshaStorage storageConfig = customStorageConfig(Map.of(
        "socketTimeout", socketTimeoutSec,
        "connectTimeout", connectionTimeoutSec,
        "stmtTimeout", stmtTimeoutSec
    ));

    // And:
    IStorage customStorage = registeredStorageWithConfig(storageConfig);

    // And:
    DefaultStorageHandler handler = storageHandler(handlerProperties(customStorage.getId()));

    // When
    handler.processEvent(event(writeRandomFeature()));

    // Then
    ArgumentCaptor<SessionOptions> sessionOptionsCaptor = ArgumentCaptor.forClass(SessionOptions.class);
    verify(customStorage).useWriteSession(sessionOptionsCaptor.capture(), any());

    // And
    SessionOptions sessionOptions = sessionOptionsCaptor.getValue();
    assertEquals(connectionTimeoutSec * 1000, sessionOptions.connectTimeout);
    assertEquals(socketTimeoutSec * 1000, sessionOptions.socketTimeout);
    assertEquals(stmtTimeoutSec * 1000, sessionOptions.stmtTimeout);
    assertEquals(NakshaContext.defaultLockTimeout.get(), sessionOptions.lockTimeout); // was not set
  }

  @Test
  void shouldRespectCustomStoragePropertiesOnRead() {
    // Given
    int lockTimeoutSec = 10;

    // And
    NakshaStorage storageConfig = customStorageConfig(Map.of("lockTimeout", lockTimeoutSec));

    // And:
    IStorage customStorage = registeredStorageWithConfig(storageConfig);

    // And:
    DefaultStorageHandler handler = storageHandler(handlerProperties(customStorage.getId()));

    // When
    handler.processEvent(event(readRandomFeature()));

    // Then
    ArgumentCaptor<SessionOptions> sessionOptionsCaptor = ArgumentCaptor.forClass(SessionOptions.class);
    verify(customStorage).useReadSession(sessionOptionsCaptor.capture(), any());

    // And
    SessionOptions sessionOptions = sessionOptionsCaptor.getValue();
    assertEquals(NakshaContext.defaultConnectTimeout.get(), sessionOptions.connectTimeout); // was not set
    assertEquals(NakshaContext.defaultSocketTimeout.get(), sessionOptions.socketTimeout); // was not set
    assertEquals(NakshaContext.defaultStmtTimeout.get(), sessionOptions.stmtTimeout); // was not set
    assertEquals(lockTimeoutSec * 1000, sessionOptions.lockTimeout);
  }

  private IStorage registeredStorageWithConfig(NakshaStorage config) {
    String customStorageId = "customStorageId_" + RandomUtils.nextInt();
    IStorage customStorage = Mockito.mock(IStorage.class);
    when(customStorage.getId()).thenReturn(customStorageId);
    when(customStorage.getConfig()).thenReturn(config);
    when(naksha.getStorageById(customStorageId)).thenReturn(customStorage);
    return customStorage;
  }

  private static Write findSingleCreateCollectionWrite(List<WriteRequest> writeRequests) {
    List<Write> collectionWrites = getSingularWritesToCollection(writeRequests, Naksha.COLLECTIONS_COL);
    assertEquals(1, collectionWrites.size(), "Expected single collection write");
    return collectionWrites.get(0);
  }

  private static NakshaCollection collectionFrom(Write write) {
    assertInstanceOf(NakshaCollection.class, write.getFeature());
    return (NakshaCollection) write.getFeature();
  }

  private static void assertHubSlimIndices(NakshaCollection collection) {
    assertIndices(collection, "id", "tags", "gist_geo", "next_version");
  }

  private static void assertIndices(NakshaCollection collection, String... expectedIndices) {
    StringList indices = collection.getIndices();
    assertNotNull(indices);
    assertTrue(indices.containsStringsInOrder(expectedIndices));
  }

  private static List<Write> getSingularWritesToCollection(List<WriteRequest> writeRequests, String collectionId) {
    return flattenSingularWriteRequest(writeRequests)
        .filter(write -> collectionId.equals(write.getCollectionId()))
        .collect(Collectors.toList());
  }

  private static Stream<Write> flattenSingularWriteRequest(List<WriteRequest> writeRequests) {
    return writeRequests.stream()
        .filter(wr -> wr.getWrites().size() == 1)
        .map(wr -> wr.getWrites().get(0));
  }

  private static ArgumentMatcher<WriteRequest> matchesCreateCollectionRequest() {
    return writeRequest -> {
      WriteList writes = writeRequest.getWrites();
      return writes.size() == 1 && Naksha.COLLECTIONS_COL.equals(writes.get(0).getCollectionId());
    };
  }

  private static Stream<Named<CollectionPriorityTestCase>> collectionPriorityTestCases() {
    return Stream.of(
        named(
            "Collection from Handler has higher priority than collection from Space",
            new CollectionPriorityTestCase(
                handlerPropertiesWithCollection("handler_collection"),
                space("test_space", spacePropertiesWithCollection("space_collection_id")),
                ValidCollectionSource.HANDLER_PROPERTIES
            )
        ),
        named(
            "Collection from Space is used when collection in Handler is undefined",
            new CollectionPriorityTestCase(
                handlerPropertiesWithCollection(null),
                space("test_space", spacePropertiesWithCollection("space_collection_id")),
                ValidCollectionSource.SPACE_PROPERTIES
            )
        ),
        named("Collection from Handler is used when collection in Space was undefined",
            new CollectionPriorityTestCase(
                handlerPropertiesWithCollection("handler_collection"),
                space("test_space", spacePropertiesWithCollection(null)),
                ValidCollectionSource.HANDLER_PROPERTIES
            )
        )
        /* TODO: Uncomment & fix as part of CASL-968 / CASL-971
        ,
        named("Collection with id based on Event Target is used when no collection is defined in Space or Handler properties",
            new CollectionPriorityTestCase(
                handlerPropertiesWithCollection(null),
                space("test_space", spacePropertiesWithCollection(null)),
                ValidCollectionSource.SPACE_ID
            )
        )
         */
    );
  }

  private NakshaStorage customStorageConfig(Map<String, Object> customProps) {
    NakshaStorage storageConfig = new NakshaStorage();
    NakshaProperties properties = storageConfig.getProperties();
    customProps.forEach((key, value) -> {
      properties.put(key, value);
    });
    return storageConfig;
  }

  static final class CollectionPriorityTestCase {

    private final DefaultStorageHandlerProperties handlerProperties;
    private final Space space;
    private final ValidCollectionSource validCollectionSource;

    CollectionPriorityTestCase(
        DefaultStorageHandlerProperties handlerProperties,
        Space space,
        ValidCollectionSource validCollectionSource
    ) {
      this.handlerProperties = handlerProperties;
      this.space = space;
      this.validCollectionSource = validCollectionSource;
    }

    enum ValidCollectionSource {
      HANDLER_PROPERTIES,
      SPACE_PROPERTIES,
      SPACE_ID
    }

    NakshaCollection correctCollection() {
      switch (validCollectionSource) {
        case HANDLER_PROPERTIES:
          return handlerProperties.getCollection();
        case SPACE_PROPERTIES:
          return JvmBoxingUtil.box(space.getProperties(), SpaceProperties.class).getCollection();
        case SPACE_ID:
          return new NakshaCollection(space.getId()).withMapId(getMapId());
        default:
          throw new IllegalStateException("Unexpected collection source: " + validCollectionSource);
      }
    }
  }

  private static boolean isOnlyWriteFeaturesRequest(Request request) {
    if (!(request instanceof WriteRequest)) {
      return false;
    }
    return RequestTypesUtil.isOnlyWriteFeatures((WriteRequest) request);
  }

  private static boolean isOnlyWriteCollectionsRequest(Request request) {
    if (!(request instanceof WriteRequest)) {
      return false;
    }
    return RequestTypesUtil.isOnlyWriteCollections((WriteRequest) request);
  }

  private IEvent event(Request request) {
    IEvent dummy = mock(IEvent.class);
    when(dummy.getRequest()).thenReturn(request);
    return dummy;
  }

  private static Request writeRandomFeature() {
    return new WriteRequest()
        .add(new Write().createFeature(
            "random_collection_" + RandomUtils.nextInt(),
            new NakshaFeature("random_feature_" + RandomUtils.nextInt())
        ));
  }

  private static Request readRandomFeature() {
    ReadFeatures readFeatures = new ReadFeatures();
    readFeatures.setMapId("random_map_" + RandomUtils.nextInt());
    readFeatures.setCollectionIds(new StringList("random_collection_" + RandomUtils.nextInt()));
    readFeatures.setFeatureIds(new StringList("random_feature_" + RandomUtils.nextInt()));
    return readFeatures;
  }

  private static DefaultStorageHandlerProperties handlerProperties() {
    return handlerProperties("test_storage_id");
  }

  private static DefaultStorageHandlerProperties handlerPropertiesWithoutStorageId() {
    return handlerProperties(null);
  }

  private static Space space(String spaceId, SpaceProperties spaceProperties) {
    Space space = new Space();
    space.setId(spaceId);
    space.setProperties(spaceProperties);
    return space;
  }

  private static SpaceProperties spacePropertiesWithCollection(String collectionId) {
    if (collectionId == null) {
      return new SpaceProperties();
    }
    final NakshaCollection nakshaCollection = new NakshaCollection();
    nakshaCollection.setId(collectionId);
    nakshaCollection.setMapId(getMapId());
    SpaceProperties spaceProperties = new SpaceProperties();
    spaceProperties.setCollection(nakshaCollection);
    return spaceProperties;
  }

  private static DefaultStorageHandlerProperties handlerPropertiesWithCollection(String collectionId) {
    DefaultStorageHandlerProperties properties = handlerProperties();
    NakshaCollection collection = collectionId != null ? new NakshaCollection() : null;
    if (collection != null) {
      collection.setId(collectionId);
      collection.setMapId(getMapId());
    }
    properties.setCollection(collection);
    return properties;
  }

  private static DefaultStorageHandlerProperties handlerProperties(String storageId) {
    final NakshaCollection nakshaCollection = new NakshaCollection();
    nakshaCollection.setId("handler_collection");
    nakshaCollection.setMapId(getMapId());
    DefaultStorageHandlerProperties properties = new DefaultStorageHandlerProperties();
    properties.setStorageId(storageId);
    properties.setCollection(nakshaCollection);
    properties.setAutoDeleteCollection(true);
    properties.setAutoCreateCollection(true);
    return properties;
  }

  private static WriteRequest updateCollectionRequest(String collectionId) {
    WriteRequest wr = new WriteRequest();
    wr.add(new Write().upsertCollection(new NakshaCollection(collectionId)));
    return wr;
  }

  private static WriteRequest deleteCollectionRequest(String collectionId) {
    WriteRequest wr = new WriteRequest();
    wr.add(new Write().deleteCollection(new NakshaCollection(collectionId), false));
    return wr;
  }

  private static ArgumentMatcher<WriteRequest> matchesCreateMapRequest(String mapId) {
    return wr -> {
      WriteList writes = wr.getWrites();
      if (writes.size() != 1) {
        return false;
      }
      Write w = writes.get(0);
      return WriteOp.CREATE.equals(w.getOp())
             && Naksha.ADMIN_MAP.equals(w.getMapId())
             && Naksha.MAPS_COL.equals(w.getCollectionId())
             && mapId.equals(w.getId());
    };
  }

  private DefaultStorageHandler storageHandler() {
    return storageHandler(testSpace());
  }

  private DefaultStorageHandler storageHandler(DefaultStorageHandlerProperties properties) {
    return storageHandler(properties, testSpace());
  }

  private Space testSpace() {
    Space space = new Space();
    space.setId("some_test_space");
    return space;
  }

  private DefaultStorageHandler storageHandler(Space space) {
    return storageHandler(handlerProperties(), space);
  }

  private DefaultStorageHandler storageHandler(DefaultStorageHandlerProperties properties, Space space) {
    EventHandlerConfig config = new EventHandlerConfig();
    config.setClassName(DefaultStorageHandler.class.getName());
    config.setId(HANDLER_ID);
    config.setProperties(properties);
    return new DefaultStorageHandler(config, naksha, space);
  }

  private void configureStorageSessionMocks() {
    when(naksha.getStorageById(any())).thenReturn(storage);
    when(storage.newWriteSession(any(SessionOptions.class))).thenReturn(storageWriteSession);
    when(storage.useWriteSession(any(SessionOptions.class), any(Fn1.class))).thenCallRealMethod();
    doNothing().when(storage).runInWriteSession(any(SessionOptions.class), any(Fx1.class));
    when(storage.newReadSession(any(SessionOptions.class))).thenReturn(storageReadSession);
    when(storage.useReadSession(any(SessionOptions.class), any(Fn1.class))).thenCallRealMethod();
    doNothing().when(storage).runInReadSession(any(SessionOptions.class), any(Fx1.class));
  }

  private NakshaStorage storageConfigWithMapId(@Nullable String mapId) {
    NakshaStorage config = new NakshaStorage();
    config.getProperties().put("schema", mapId);
    return config;
  }

  private void configureStorageConfig(@Nullable NakshaStorage config) {
    when(storage.getConfig()).thenReturn(config);
  }

  private void ignoreExceptionsFrom(Callable<?> callable, String reason) {
    try {
      callable.call();
    } catch (Exception e) {
      log.info("Encountered exception that will be ignored on test level because: {}", reason, e);
    }
  }
}
