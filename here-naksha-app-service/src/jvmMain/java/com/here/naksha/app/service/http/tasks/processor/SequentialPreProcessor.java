package com.here.naksha.app.service.http.tasks.processor;

import naksha.model.objects.NakshaFeature;

public final class SequentialPreProcessor<T extends NakshaFeature> implements FeaturePreProcessor<T> {

  private final FeaturePreProcessor<T>[] preProcessors;

  private SequentialPreProcessor(FeaturePreProcessor<T>[] preProcessors) {
    this.preProcessors = preProcessors;
  }

  public static <T extends NakshaFeature> SequentialPreProcessor<T> combine(FeaturePreProcessor<T>... preProcessors) {
    return new SequentialPreProcessor<>(preProcessors);
  }

  @Override
  public T preProcess(T feature) {
    for (FeaturePreProcessor<T> preProcessor : preProcessors) {
      if (preProcessor != null) {
        feature = preProcessor.preProcess(feature);
      }
    }
    return feature;
  }
}