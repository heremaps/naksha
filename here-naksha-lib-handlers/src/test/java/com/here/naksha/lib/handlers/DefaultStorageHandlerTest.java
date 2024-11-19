package com.here.naksha.lib.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Named.named;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.here.naksha.lib.core.IEvent;
import com.here.naksha.lib.core.INaksha;
import naksha.base.JvmProxyUtil;
import naksha.model.*;
import com.here.naksha.lib.core.models.naksha.EventHandler;
import com.here.naksha.lib.core.models.naksha.Space;
import com.here.naksha.lib.core.models.naksha.SpaceProperties;
import com.here.naksha.lib.core.models.storage.EWriteOp;
import com.here.naksha.lib.handlers.DefaultStorageHandlerTest.CollectionPriorityTestCase.ValidCollectionSource;
import java.sql.SQLException;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

import naksha.model.objects.NakshaCollection;
import naksha.model.objects.NakshaFeature;
import naksha.model.request.*;
import org.apache.commons.lang3.RandomUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class DefaultStorageHandlerTest {

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
    configureStorageMocks();
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
    WriteRequest writeXyzFeatures = new WriteRequest().add(new Write().createFeature(null, "different_collection", featureToCreate));

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
    // Given: Storage writer failing on WriteXyzFeatures due to undefined table but is able to create new collection
    when(storageWriteSession.execute(any(WriteXyzFeatures.class)))
        .thenThrow(new RuntimeException(new SQLException("Some message", EPsqlState.UNDEFINED_TABLE.toString())));
    when(storageWriteSession.execute(any(WriteXyzCollections.class))).thenReturn(new SuccessResult());

    // And: feature to be saved in potentially different collection
    XyzFeature featureToCreate = new XyzFeature("sample_feature");
    WriteXyzFeatures writeXyzFeatures = new WriteXyzFeatures("different_collection").create(featureToCreate);

    // And: Handler with autoCreateCollection enabled to test
    DefaultStorageHandler handler = storageHandler(testCase.handlerProperties, testCase.space);
    assertTrue(handler.properties.getAutoCreateCollection());

    // When: Processing write features
    ignoreExceptionsFrom(
        () -> handler.processEvent(event(writeXyzFeatures)),
        "The mock for storage writer is already configured to always fail - it's ok to allow this as we only want to check invocations"
    );

    // Then: Write Collection request was passed to storage writer
    ArgumentCaptor<WriteXyzCollections> storageWriterRequestCaptor = ArgumentCaptor.forClass(WriteXyzCollections.class);
    verify(storageWriteSession).execute(storageWriterRequestCaptor.capture());

    // And: passed Write Collection request was about creating single collection with correct id
    WriteXyzCollections requestPassedToStorageWriter = storageWriterRequestCaptor.getValue();
    assertEquals(1, requestPassedToStorageWriter.features.size());
    assertEquals(EWriteOp.CREATE.toString(), requestPassedToStorageWriter.features.get(0).getOp());
    assertEquals(testCase.correctCollection(), requestPassedToStorageWriter.features.get(0).getFeature());
  }

  @ParameterizedTest
  @MethodSource("sqlErrorsIndicatingMissingCollection")
  void shouldCreateMissingCollectionDueToErrorSqlState(SQLException writerFailureCause) {
    // Given: Storage writer failing on WriteXyzFeatures due to sql exception
    when(storageWriteSession.execute(any(WriteRequest.class))).thenThrow(new RuntimeException(writerFailureCause));

    // And: feature to be saved in potentially different collection
    NakshaFeature featureToCreate = new NakshaFeature("sample_feature");
    WriteRequest writeXyzFeatures = new WriteRequest().add(new Write().createFeature(null, "different_collection", featureToCreate));

    // And: Handler with autoCreateCollection enabled to test
    DefaultStorageHandler handler = storageHandler();
    assertTrue(handler.properties.getAutoCreateCollection());

    // When: Processing write features
    ignoreExceptionsFrom(
        () -> handler.processEvent(event(writeXyzFeatures)),
        "The mock for storage writer is already configured to always fail - it's ok to allow this as we only want to check invocations"
    );

    // Then: Write Collection request was passed to storage writer
    ArgumentCaptor<WriteRequest> storageWriterRequestCaptor = ArgumentCaptor.forClass(WriteRequest.class);
    verify(storageWriteSession).execute(storageWriterRequestCaptor.capture());

    // And: passed Write Collection request was about creating collection defined in Handler properties
    WriteRequest requestPassedToStorageWriter = storageWriterRequestCaptor.getValue();
    assertEquals(1, requestPassedToStorageWriter.getWrites().size());
    assertEquals(WriteOp.CREATE, requestPassedToStorageWriter.getWrites().get(0).getOp());
    assertEquals(handler.properties.getCollection(), requestPassedToStorageWriter.getWrites().get(0).getFeature());
  }

  @ParameterizedTest
  @MethodSource("sqlErrorsIndicatingMissingCollection")
  void shouldNotCreateCollectionWhenAutoCreateIsDisabled(SQLException writerFailureCause) {
    // Given: Storage writer failing on WriteXyzFeatures due to sql exception
    when(storageWriteSession.execute(any(WriteRequest.class))).thenThrow(new RuntimeException(writerFailureCause));

    // And: feature to be saved in potentially different collection
    NakshaFeature featureToCreate = new NakshaFeature("sample_feature");
    WriteRequest writeXyzFeatures = new WriteRequest().add(new Write().createFeature(null, "different_collection", featureToCreate));

    // And: Handler with autoCreateCollection enabled to test
    DefaultStorageHandler handler = storageHandler();
    handler.properties.setAutoCreateCollection(false);

    // When: Processing write features
    ignoreExceptionsFrom(
        () -> handler.processEvent(event(writeXyzFeatures)),
        "The mock for storage writer is already configured to always fail - it's ok to allow this as we only want to check invocations"
    );

    // Then: No Write Collection request was passed to storage writer
    verify(storageWriteSession, never()).execute(any(WriteRequest.class));
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
        ),
        named("Collection with id based on Event Target is used when no collection is defined in Space or Handler properties",
            new CollectionPriorityTestCase(
                handlerPropertiesWithCollection(null),
                space("test_space", spacePropertiesWithCollection(null)),
                ValidCollectionSource.SPACE_ID
            )
        )
    );
  }

  private static Stream<SQLException> sqlErrorsIndicatingMissingCollection() {
    return Stream.of(
        new SQLException("Collection does not exist", EPsqlState.COLLECTION_DOES_NOT_EXIST.toString()),
        new SQLException("Undefined table", EPsqlState.UNDEFINED_TABLE.toString())
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
        case SPACE_PROPERTIES -> JvmProxyUtil.box(space.getProperties(), SpaceProperties.class).getCollection();
        case SPACE_ID -> new NakshaCollection(space.getId());
      };
    }
  }

  private Space space(SpaceProperties spaceProperties) {
    Space space = new Space("test_space");
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
                    null,
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
    Space space = new Space(spaceId);
    space.setProperties(spaceProperties);
    return space;
  }

  private static SpaceProperties spacePropertiesWithCollection(String collectionId) {
    if (collectionId == null) {
      return new SpaceProperties(null);
    }
    final NakshaCollection nakshaCollection = new NakshaCollection();
    nakshaCollection.setId(collectionId);
    return new SpaceProperties(nakshaCollection);
  }

  private static DefaultStorageHandlerProperties handlerPropertiesWithCollection(String collectionId) {
    DefaultStorageHandlerProperties properties = handlerProperties();
    NakshaCollection collection = collectionId != null ? new NakshaCollection() : null;
    collection.setId(collectionId);
    properties.setCollection(collection);
    return properties;
  }

  private static DefaultStorageHandlerProperties handlerProperties(String storageId) {
    final NakshaCollection nakshaCollection = new NakshaCollection();
    nakshaCollection.setId("handler_collection");
    return new DefaultStorageHandlerProperties(
        storageId,
            nakshaCollection,
        true,
        true
    );
  }

  private DefaultStorageHandler storageHandler() {
    return storageHandler(new Space("some_test_space"));
  }

  private DefaultStorageHandler storageHandler(DefaultStorageHandlerProperties properties) {
    return storageHandler(properties, new Space("some_test_space"));
  }

  private DefaultStorageHandler storageHandler(Space space) {
    return storageHandler(handlerProperties(), space);
  }

  private DefaultStorageHandler storageHandler(DefaultStorageHandlerProperties properties, Space space) {
    EventHandler config = new EventHandler(DefaultStorageHandler.class, "test_handler");
    config.setProperties(properties);
    return new DefaultStorageHandler(config, naksha, space);
  }

  private void configureStorageMocks() {
    when(naksha.getStorageById(any())).thenReturn(storage);
    when(storage.newWriteSession(any(SessionOptions.class))).thenReturn(storageWriteSession);
    when(storage.newReadSession(any(SessionOptions.class))).thenReturn(storageReadSession);
  }

  private void ignoreExceptionsFrom(Callable<?> callable, String reason) {
    try {
      callable.call();
    } catch (Exception e) {
      log.info("Encountered exception that will be ignored on test level because: {}", reason, e);
    }
  }
}
