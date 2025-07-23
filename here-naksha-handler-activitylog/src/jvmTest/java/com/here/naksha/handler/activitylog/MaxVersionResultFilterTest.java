package com.here.naksha.handler.activitylog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import naksha.base.JvmInt64;
import naksha.model.TupleNumber;
import naksha.model.Version;
import naksha.model.objects.NakshaFeature;
import naksha.model.request.FeatureTuple;
import org.junit.jupiter.api.Test;

class MaxVersionResultFilterTest {

  private final MaxVersionResultFilter filter = new MaxVersionResultFilter(
      Map.of(
          "f1", Version.of(2025, 7, 1, new JvmInt64(0)),
          "f2", Version.of(2025, 7, 2, new JvmInt64(0)),
          "f3", Version.of(2025, 7, 3, new JvmInt64(0))
      )
  );

  @Test
  void shouldExcludeTupleIfVersionTooHigh() {
    // Given
    FeatureTuple featureTuple = featureTuple(
        "f2",
        Version.of(2025, 7, 3, new JvmInt64(0)) // f2 has limit to 2025-7-2
    );

    // When, Then
    assertNull(filter.filter(featureTuple));
  }

  @Test
  void shouldIncludeTupleIfVersionIsEqual() {
    // Given
    FeatureTuple featureTuple = featureTuple(
        "f2",
        Version.of(2025, 7, 2, new JvmInt64(0)) // f2 has limit to 2025-7-2
    );

    // When, Then
    assertEquals(featureTuple, filter.filter(featureTuple));
  }

  @Test
  void shouldIncludeTupleIfVersionIsSmaller() {
    // Given
    FeatureTuple featureTuple = featureTuple(
        "f2",
        Version.of(2025, 7, 1, new JvmInt64(0)) // f2 has limit to 2025-7-2
    );

    // When, Then
    assertEquals(featureTuple, filter.filter(featureTuple));
  }

  @Test
  void shouldIncludeTupleIfVersionIsUndefined() {
    // Given
    FeatureTuple featureTuple = featureTuple(
        "f4", // no entry for f4
        Version.of(2025, 7, 1, new JvmInt64(0))
    );

    // When, Then
    assertEquals(featureTuple, filter.filter(featureTuple));
  }

  private FeatureTuple featureTuple(String featureId, Version version) {
    NakshaFeature feature = new NakshaFeature(featureId);
    TupleNumber tupleNumber = new TupleNumber(new JvmInt64(0), 0, 0, new JvmInt64(0), version, 0);
    FeatureTuple featureTuple = new FeatureTuple(tupleNumber, null);
    featureTuple.setFeature(feature);
    return featureTuple;
  }
}