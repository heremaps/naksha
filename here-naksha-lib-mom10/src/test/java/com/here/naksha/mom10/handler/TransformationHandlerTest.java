package com.here.naksha.mom10.handler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.here.naksha.lib.core.IEvent;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.models.geojson.implementation.XyzProperties;
import com.here.naksha.lib.core.models.storage.ReadCollections;
import com.here.naksha.lib.core.models.storage.ReadFeatures;
import com.here.naksha.lib.core.models.storage.Request;
import com.here.naksha.lib.core.models.storage.WriteXyzCollections;
import com.here.naksha.lib.core.models.storage.WriteXyzFeatures;
import com.here.naksha.mom10.MetaProperties;
import com.here.naksha.mom10.TransformationSamples;
import com.here.naksha.mom10.TransformationSamples.TransformationSample;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

abstract class TransformationHandlerTest {

  private static final Random random = new Random();
  protected final INaksha naksha;

  protected TransformationHandlerTest() {
    naksha = mock(INaksha.class);
  }

  protected IEvent event(Request request) {
    IEvent event = mock(IEvent.class);
    when(event.getRequest()).thenReturn(request);
    return event;
  }

  protected Stream<TransformationSample> samplesWithModelVersion(@NotNull String modelVersion) {
    return TransformationSamples.streamSamples()
        .peek(sample -> {
          forceModelVersion(sample.original(), modelVersion);
          forceModelVersion(sample.transformed(), modelVersion);
        });
  }

  private void forceModelVersion(@NotNull XyzFeature feature, @NotNull String modelVersion) {
    XyzProperties properties = feature.getProperties();
    if(properties.containsKey(MetaProperties.META)){
      Map meta = (Map) properties.get(MetaProperties.META);
      meta.put(MetaProperties.MODEL_VERSION, modelVersion);
    }
    if(properties.containsKey(XyzProperties.HERE_META_NS)){
      Map meta = (Map) properties.get(XyzProperties.HERE_META_NS);
      meta.put(MetaProperties.MODEL_VERSION, modelVersion);
    }
  }

  protected static List<XyzFeature> randomFeatures() {
    return randomFeatures(random.nextInt(10) + 1);
  }

  protected static List<XyzFeature> randomFeatures(int count) {
    int size = random.nextInt(10) + 1;
    List<XyzFeature> features = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      XyzFeature feature = new XyzFeature();
      feature.setId("feature" + i);
      features.add(feature);
    }
    return features;
  }

  protected static Stream<List<XyzFeature>> nullAndEmptyList() {
    return Stream.of(null, Collections.emptyList());
  }

  protected static Stream<Request<?>> commonInvalidRequests() {
    return Stream.of(
        new ReadFeatures("some_collection"),
        new ReadCollections(),
        new WriteXyzFeatures("some_collection"),
        new WriteXyzCollections()
    );
  }
}
