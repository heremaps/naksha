package com.here.naksha.lib.handlers;

import static naksha.model.NakshaError.COLLECTION_NOT_FOUND;
import static naksha.model.NakshaError.MAP_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import naksha.base.JvmBoxingUtil;
import naksha.base.fn.Fn1;
import naksha.base.fn.Fx1;
import naksha.model.IReadSession;
import naksha.model.IStorage;
import naksha.model.IWriteSession;
import naksha.model.Naksha;
import naksha.model.NakshaError;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaCollection;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaStorage;
import naksha.model.request.ErrorResponse;
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
    WriteRequest writeXyzFeatures = new WriteRequest().add(new Write().createFeature("different_map", "different_collection", featureToCreate));

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
        storageWriteSession.execute(argThat(request -> (request instanceof WriteRequest wr) && (RequestTypesUtil.isOnlyWriteFeatures(wr)))))
        .thenReturn(new ErrorResponse(missingCollectionError));
    when(storageWriteSession.execute(
        argThat(request -> (request instanceof WriteRequest wr) && (RequestTypesUtil.isOnlyWriteCollections(wr)))))
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
  void shouldFailWhenStorageConfigHasNoMapId() {
    // Given:
    configureStorageConfig(new NakshaStorage());

    // And
    DefaultStorageHandler handler = storageHandler();

    // When:
    Response response = handler.process(event(writeRandomFeature()));

    // Then:
    assertInstanceOf(ErrorResponse.class, response);
    ErrorResponse errorResponse = (ErrorResponse) response;
    assertEquals(NakshaError.ILLEGAL_STATE, errorResponse.getError().getCode());
    assertEquals(
        "Unable to determine 'mapId' for handler '" + HANDLER_ID + "' and through associated storage '" + STORAGE_ID + "'.",
        errorResponse.getError().getMsg()
    );
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

  private static Write findSingleCreateCollectionWrite(List<WriteRequest> writeRequests) {
    List<Write> collectionWrites = getSingularWritesToCollection(writeRequests, Naksha.COLLECTIONS_COL);
    assertEquals(1, collectionWrites.size(), "Expected single collection write");
    return collectionWrites.get(0);
  }

  private static List<Write> getSingularWritesToCollection(List<WriteRequest> writeRequests, String collectionId) {
    return flattenSingularWriteRequest(writeRequests)
        .filter(write -> collectionId.equals(write.getCollectionId()))
        .toList();
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

  record CollectionPriorityTestCase(
      DefaultStorageHandlerProperties handlerProperties,
      Space space,
      ValidCollectionSource validCollectionSource

  ) {

    enum ValidCollectionSource {
      HANDLER_PROPERTIES,
      SPACE_PROPERTIES,
      SPACE_ID
    }

    NakshaCollection correctCollection() {
      return switch (validCollectionSource) {
        case HANDLER_PROPERTIES -> handlerProperties.getCollection();
        case SPACE_PROPERTIES -> JvmBoxingUtil.box(space.getProperties(), SpaceProperties.class).getCollection();
        case SPACE_ID -> new NakshaCollection(space.getId()).withMapId(getMapId());
      };
    }
  }

  private Space space(SpaceProperties spaceProperties) {
    Space space = testSpace();
    space.setProperties(spaceProperties);
    return space;
  }

  private IEvent event(Request request) {
    IEvent dummy = mock(IEvent.class);
    when(dummy.getRequest()).thenReturn(request);
    return dummy;
  }

  private Request writeRandomFeature() {
    return new WriteRequest()
        .add(new Write().createFeature(
            "random_collection_" + RandomUtils.nextInt(),
            new NakshaFeature("random_feature_" + RandomUtils.nextInt())
        ));
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
