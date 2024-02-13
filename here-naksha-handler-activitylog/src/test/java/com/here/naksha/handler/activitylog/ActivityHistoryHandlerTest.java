package com.here.naksha.handler.activitylog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.here.naksha.lib.core.IEvent;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.models.XyzError;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.models.naksha.EventHandler;
import com.here.naksha.lib.core.models.naksha.EventTarget;
import com.here.naksha.lib.core.models.naksha.XyzCollection;
import com.here.naksha.lib.core.models.storage.ErrorResult;
import com.here.naksha.lib.core.models.storage.ReadCollections;
import com.here.naksha.lib.core.models.storage.Request;
import com.here.naksha.lib.core.models.storage.Result;
import com.here.naksha.lib.core.models.storage.SuccessResult;
import com.here.naksha.lib.core.models.storage.WriteXyzCollections;
import com.here.naksha.lib.core.models.storage.WriteXyzFeatures;
import com.here.naksha.lib.core.storage.IStorage;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

class ActivityHistoryHandlerTest {

  @Mock
  INaksha naksha;

  @Mock
  EventHandler eventHandler;

  @Mock
  EventTarget<?> eventTarget;

  @Mock
  IStorage storage;

  private ActivityHistoryHandler handler;

  @BeforeEach
  void setup() {
    MockitoAnnotations.openMocks(this);
    when(naksha.getStorageById(any())).thenReturn(storage);
    handler = new ActivityHistoryHandler(eventHandler, naksha, eventTarget);
  }

  @ParameterizedTest
  @MethodSource("unhandledRequests")
  void shouldFailOnNonReadFeaturesRequests(Request<?> unhandledRequest) {
    // Given:
    IEvent event = eventWith(unhandledRequest);

    // When:
    Result result = handler.processEvent(event);

    // Then: Storage was not used at all
    verifyNoInteractions(storage);

    // And: Error result (NOT_IMPLEMENTED) was returned
    assertInstanceOf(ErrorResult.class, result);
    assertEquals(XyzError.NOT_IMPLEMENTED, ((ErrorResult) result).reason);
  }

  @Test
  void shouldImmediatelySucceedOnSingleCollectionDelete() {
    // Given:
    IEvent event = eventWith(new WriteXyzCollections().delete(new XyzCollection("some_collection")));

    // When:
    Result result = handler.processEvent(event);

    // Then: Storage was not used at all
    verifyNoInteractions(storage);

    // And: SuccessResult was returned
    assertInstanceOf(SuccessResult.class, result);
  }

  @Test
  void shouldProcessHistory() {
    // Given:

  }


  private IEvent eventWith(Request request) {
    IEvent event = Mockito.mock(IEvent.class);
    when(event.getRequest()).thenReturn(request);
    return event;
  }

  private static Stream<Request<?>> unhandledRequests() {
    return Stream.of(
        new WriteXyzFeatures("some_collection").create(new XyzFeature("some_feature")),
        new WriteXyzCollections().create(new XyzCollection("some_collection")),
        new ReadCollections().withIds("some_collection")
    );
  }
}