package com.here.naksha.handler.activitylog;

import static com.here.naksha.handler.activitylog.ActivityLogRequestTranslationUtil.ACTION;
import static com.here.naksha.handler.activitylog.ActivityLogRequestTranslationUtil.CREATED_AT;
import static com.here.naksha.handler.activitylog.ActivityLogRequestTranslationUtil.PUUID;
import static com.here.naksha.handler.activitylog.ActivityLogRequestTranslationUtil.UPDATED_AT;
import static com.here.naksha.handler.activitylog.ActivityLogRequestTranslationUtil.UUID;
import static naksha.model.XyzNs.NUUID;

import java.util.Map;
import naksha.base.JvmInt64;
import naksha.base.Action;
import naksha.model.XyzNs;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaProperties;

class NakshaFeatureBuilder {

  private final NakshaFeature feature;

  private NakshaFeatureBuilder(NakshaFeature feature) {
    this.feature = feature;
  }

  static NakshaFeatureBuilder nakshaFeature(String id) {
    NakshaFeature feature = new NakshaFeature(id);
    NakshaProperties properties = new NakshaProperties();
    properties.setXyz(new XyzNs());
    feature.setProperties(properties);
    return new NakshaFeatureBuilder(feature);
  }

  NakshaFeature build() {
    return feature;
  }

  NakshaFeatureBuilder withUuid(String uuid) {
    feature.getProperties().getXyz().put(UUID, uuid);
    return this;
  }

  /**
   * `puuid` is no longer populated by Naksha {@code lib-psql}, it will just be a custom JSON attribute assigned by users.
   * @deprecated since 3.0.0-beta.41
   */
  @Deprecated(since = "3.0.0-beta.41")
  NakshaFeatureBuilder withPuuid(String puuid) {
    feature.getProperties().getXyz().put(PUUID, puuid);
    return this;
  }

  NakshaFeatureBuilder withNuuid(String nuuid) {
    feature.getProperties().getXyz().put(NUUID, nuuid);
    return this;
  }

  NakshaFeatureBuilder withAction(Action action) {
    feature.getProperties().getXyz().put(ACTION, action);
    return this;
  }

  NakshaFeatureBuilder withCreatedAt(JvmInt64 createdAt) {
    feature.getProperties().getXyz().put(CREATED_AT, createdAt);
    return this;
  }

  NakshaFeatureBuilder withUpdatedAt(JvmInt64 updatedAt) {
    feature.getProperties().getXyz().put(UPDATED_AT, updatedAt);
    return this;
  }

  NakshaFeatureBuilder withCustomProperties(Map properties) {
    feature.getProperties().putAll(properties);
    return this;
  }
}
