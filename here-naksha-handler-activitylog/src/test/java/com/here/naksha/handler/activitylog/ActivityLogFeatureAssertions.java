package com.here.naksha.handler.activitylog;

import static java.util.Collections.emptyMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.models.geojson.implementation.XyzProperties;
import com.here.naksha.lib.core.models.geojson.implementation.namespaces.XyzActivityLog;
import com.here.naksha.lib.core.util.json.Json;
import com.here.naksha.lib.core.util.json.JsonSerializable;
import java.util.Map;
import org.json.JSONException;
import org.junit.jupiter.api.Assertions;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

public class ActivityLogFeatureAssertions {

  private final XyzFeature subject;

  private ActivityLogFeatureAssertions(XyzFeature subject) {
    this.subject = subject;
  }

  static ActivityLogFeatureAssertions assertThatActivityLogFeature(XyzFeature xyzFeature) {
    assertNotNull(xyzFeature);
    return new ActivityLogFeatureAssertions(xyzFeature);
  }

  ActivityLogFeatureAssertions hasId(String id) {
    Assertions.assertEquals(id, subject.getId());
    return this;
  }

  ActivityLogFeatureAssertions hasActivityLogId(String id) {
    assertNotNull(subject.getProperties().getXyzActivityLog());
    Assertions.assertEquals(id, subject.getProperties().getXyzActivityLog().getId());
    return this;
  }

  ActivityLogFeatureAssertions hasAction(String action) {
    assertNotNull(subject.getProperties().getXyzActivityLog());
    Assertions.assertEquals(action, subject.getProperties().getXyzActivityLog().getAction());
    return this;
  }

  ActivityLogFeatureAssertions hasReversePatch(JsonNode reversePatch) {
    assertNotNull(subject.getProperties().getXyzActivityLog());
    Assertions.assertEquals(reversePatch, subject.getProperties().getXyzActivityLog().getDiff());
    return this;
  }

  ActivityLogFeatureAssertions isIdenticalToDatahubSampleFeature(XyzFeature otherFeature, String message) throws JSONException {
//    Map subjectAsMap = subject.asMap();
//    Map otherAsMap = otherFeature.asMap();
//    alignDiff(subjectAsMap);
    alignDiff(subject);
    String subJson = JsonSerializable.serialize(subject);
    String otherJson = JsonSerializable.serialize(otherFeature);
//    assertEquals(subjectAsMap, otherAsMap, message);
    JSONAssert.assertEquals(message, otherJson, subJson, JSONCompareMode.LENIENT);
    return this;
  }

  private static void alignDiff(XyzFeature xyzFeature) {
    JsonNode diff = xyzFeature.getProperties().getXyzActivityLog().getDiff();
    if(diff != null){
      ((ObjectNode) diff).put("copy", 0);
      ((ObjectNode) diff).put("move", 0);
    }
  }
}
