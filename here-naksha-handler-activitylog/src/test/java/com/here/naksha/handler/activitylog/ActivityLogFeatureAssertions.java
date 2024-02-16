package com.here.naksha.handler.activitylog;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import org.junit.jupiter.api.Assertions;

public class ActivityLogFeatureAssertions {

  private final XyzFeature subject;

  private ActivityLogFeatureAssertions(XyzFeature subject) {
    this.subject = subject;
  }

  static ActivityLogFeatureAssertions assertThat(XyzFeature xyzFeature) {
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
}
