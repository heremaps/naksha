package com.here.naksha.handler.activitylog.assertions;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.here.naksha.handler.activitylog.NakshaActivityLog;
import naksha.base.Platform;
import naksha.base.ToJsonOptions;
import naksha.model.objects.NakshaFeature;
import org.json.JSONException;
import org.junit.jupiter.api.Assertions;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

public class ActivityLogFeatureAssertions {

  private final NakshaFeature subject;

  private ActivityLogFeatureAssertions(NakshaFeature subject) {
    this.subject = subject;
  }

  public static ActivityLogFeatureAssertions assertThatActivityLogFeature(NakshaFeature xyzFeature) {
    assertNotNull(xyzFeature);
    return new ActivityLogFeatureAssertions(xyzFeature);
  }

  public ActivityLogFeatureAssertions hasId(String id) {
    Assertions.assertEquals(id, subject.getId());
    return this;
  }

  public ActivityLogFeatureAssertions hasActivityLogId(String id) {
    final NakshaActivityLog activityLog = NakshaActivityLog.getActivityLog(subject.getProperties());
    assertNotNull(activityLog);
    Assertions.assertEquals(id, activityLog.getId());
    return this;
  }

  public ActivityLogFeatureAssertions hasAction(String action) {
    final NakshaActivityLog activityLog = NakshaActivityLog.getActivityLog(subject.getProperties());
    assertNotNull(activityLog);
    Assertions.assertEquals(action, activityLog.getAction());
    return this;
  }

  public ActivityLogFeatureAssertions hasReversePatch(JsonNode reversePatch) {
    final NakshaActivityLog activityLog = NakshaActivityLog.getActivityLog(subject.getProperties());
    assertNotNull(activityLog);
    Assertions.assertEquals(reversePatch, activityLog.getDiff());
    return this;
  }

  public ActivityLogFeatureAssertions isIdenticalToDatahubSampleFeature(NakshaFeature datahubFeature, String message) throws JSONException {
    alignDiff(subject);
    String subjectJson = Platform.toJSON(subject, ToJsonOptions.DEFAULT);
    String datahubFeatureJson = Platform.toJSON(datahubFeature, ToJsonOptions.DEFAULT);
    JSONAssert.assertEquals(message, datahubFeatureJson, subjectJson, JSONCompareMode.LENIENT);
    return this;
  }

  private static void alignDiff(NakshaFeature xyzFeature) {
    JsonNode diff = NakshaActivityLog.getActivityLog(xyzFeature.getProperties()).getDiff();
    if(diff != null){
      ((ObjectNode) diff).put("copy", 0);
      ((ObjectNode) diff).put("move", 0);
    }
  }
}
