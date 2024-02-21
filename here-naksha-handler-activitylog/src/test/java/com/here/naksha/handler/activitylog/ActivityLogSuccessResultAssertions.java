package com.here.naksha.handler.activitylog;

import static com.here.naksha.handler.activitylog.ActivityLogFeatureAssertions.assertThatActivityLogFeature;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.models.storage.Result;
import com.here.naksha.lib.core.util.storage.ResultHelper;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Assertions;

public class ActivityLogSuccessResultAssertions {

  private final ActivityLogSuccessResult subject;

  private ActivityLogSuccessResultAssertions(ActivityLogSuccessResult subject) {
    this.subject = subject;
  }

  static ActivityLogSuccessResultAssertions assertThatResult(Result result) {
    assertNotNull(result);
    assertInstanceOf(ActivityLogSuccessResult.class, result);
    return new ActivityLogSuccessResultAssertions((ActivityLogSuccessResult) result);
  }

  @SafeVarargs
  final ActivityLogSuccessResultAssertions hasActivityFeatures(Consumer<ActivityLogFeatureAssertions>... featuresAssertions)
      throws Exception {
    List<XyzFeature> features = ResultHelper.readFeaturesFromResult(subject, XyzFeature.class);
    Assertions.assertEquals(featuresAssertions.length, features.size());
    for (int i = 0; i < featuresAssertions.length; i++) {
      featuresAssertions[i].accept(assertThatActivityLogFeature(features.get(i)));
    }
    return this;
  }

  final ActivityLogSuccessResultAssertions hasActivityFeaturesIdenticalTo(List<XyzFeature> otherFeatures)
      throws Exception {
    List<XyzFeature> features = ResultHelper.readFeaturesFromResult(subject, XyzFeature.class);
    Assertions.assertEquals(otherFeatures.size(), features.size());
    for (int i = 0; i < features.size(); i++) {
      assertThatActivityLogFeature(features.get(i))
          .isIdenticalToDatahubSampleFeature(otherFeatures.get(i), "Inequality on feature with index: " + i);
    }
    return this;
  }
}
