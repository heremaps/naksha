package com.here.naksha.handler.activitylog.assertions;

import static com.here.naksha.handler.activitylog.assertions.ActivityLogFeatureAssertions.assertThatActivityLogFeature;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.here.naksha.lib.core.util.storage.ResultHelper;
import java.util.List;
import java.util.function.Consumer;

import naksha.model.objects.NakshaFeature;
import naksha.model.request.Response;
import naksha.model.request.SuccessResponse;
import org.junit.jupiter.api.Assertions;

public class ActivityLogSuccessResultAssertions {

  private final SuccessResponse subject;

  private ActivityLogSuccessResultAssertions(SuccessResponse subject) {
    this.subject = subject;
  }

  public static ActivityLogSuccessResultAssertions assertThatResult(Response result) {
    assertNotNull(result);
    assertInstanceOf(SuccessResponse.class, result);
    return new ActivityLogSuccessResultAssertions((SuccessResponse) result);
  }

  @SafeVarargs
  public final ActivityLogSuccessResultAssertions hasActivityFeatures(Consumer<ActivityLogFeatureAssertions>... featuresAssertions)
      throws Exception {
    List<NakshaFeature> features = ResultHelper.readFeaturesFromResult(subject, NakshaFeature.class);
    Assertions.assertEquals(featuresAssertions.length, features.size());
    for (int i = 0; i < featuresAssertions.length; i++) {
      featuresAssertions[i].accept(assertThatActivityLogFeature(features.get(i)));
    }
    return this;
  }

  public final ActivityLogSuccessResultAssertions hasActivityFeaturesIdenticalTo(List<NakshaFeature> otherFeatures)
      throws Exception {
    List<NakshaFeature> features = ResultHelper.readFeaturesFromResult(subject, NakshaFeature.class);
    Assertions.assertEquals(otherFeatures.size(), features.size());
    for (int i = 0; i < features.size(); i++) {
      assertThatActivityLogFeature(features.get(i))
          .isIdenticalToDatahubSampleFeature(otherFeatures.get(i), "Inequality on feature with index: " + i);
    }
    return this;
  }
}
