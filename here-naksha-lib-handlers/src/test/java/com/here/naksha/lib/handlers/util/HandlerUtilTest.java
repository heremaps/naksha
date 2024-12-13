package com.here.naksha.lib.handlers.util;

import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import com.here.naksha.lib.handlers.val.ContextXyzFeatureResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import naksha.model.objects.NakshaFeature;
import org.junit.jupiter.api.Test;

class HandlerUtilTest {

  private final Random random = new Random();

  @Test
  void shouldCreateContextResult() {
    // Given
    List<NakshaFeature> features = randomFeatures();
    List<NakshaFeature> context = randomFeatures();
    List<NakshaFeature> violations = randomFeatures();

    // When
    ContextXyzFeatureResult contextXyzFeatureResult = HandlerUtil.createContextResultFromFeatureList(features, context, violations);

    // Then
    assertSameIds(features, contextXyzFeatureResult.getFeatures());
    assertSameIds(context, contextXyzFeatureResult.getContext());
    assertSameIds(violations, contextXyzFeatureResult.getViolations());
  }

  private void assertSameIds(List<NakshaFeature> first, List<NakshaFeature> second) {
    Set<String> firstIds = ids(first);
    Set<String> secondIds = ids(second);
    assertIterableEquals(firstIds, secondIds);
  }

  private Set<String> ids(List<NakshaFeature> features) {
    return features.stream().map(NakshaFeature::getId).collect(Collectors.toSet());
  }

  private List<NakshaFeature> randomFeatures() {
    int count = 1 + random.nextInt(10);
    List<NakshaFeature> list = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      NakshaFeature feature = new NakshaFeature();
      feature.setId("test_id_" + random.nextInt());
      list.add(feature);
    }
    return list;
  }
}
