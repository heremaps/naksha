package com.here.naksha.handler.activitylog;

import static com.here.naksha.handler.activitylog.ActivityLogRequestTranslationUtil.ACTION;
import static com.here.naksha.handler.activitylog.ActivityLogRequestTranslationUtil.CREATED_AT;
import static com.here.naksha.handler.activitylog.ActivityLogRequestTranslationUtil.PUUID;
import static com.here.naksha.handler.activitylog.ActivityLogRequestTranslationUtil.UPDATED_AT;
import static com.here.naksha.handler.activitylog.ActivityLogRequestTranslationUtil.UUID;
import static naksha.model.XyzNs.NUUID;

import java.util.Map;

import naksha.base.Int64;
import naksha.base.PlatformType;
import naksha.model.Action;
import naksha.model.XyzNs;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaProperties;
import org.jetbrains.annotations.NotNull;

class NakshaFeatureBuilder {

  private @NotNull NakshaFeature feature;

  NakshaFeatureBuilder(@NotNull String id) {
    feature = new NakshaFeature(id);
    NakshaProperties properties = new NakshaProperties();
    properties.setXyz(new XyzNs());
    feature.setProperties(properties);
  }

  static NakshaFeatureBuilder nakshaFeature(String id) {
    return new NakshaFeatureBuilder(id);
  }

  @NotNull NakshaFeature build() {
    return build(NakshaFeature.TYPE);
  }

  <F extends NakshaFeature> @NotNull F build(@NotNull PlatformType<F> type) {
    final var feature = this.feature;
    this.feature = new NakshaFeature();
    return feature.proxy(type);
  }

  NakshaFeatureBuilder withUuid(String uuid) {
    feature.getProperties().getXyz().put(UUID, uuid);
    return this;
  }

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

  NakshaFeatureBuilder withCreatedAt(Int64 createdAt) {
    feature.getProperties().getXyz().put(CREATED_AT, createdAt);
    return this;
  }

  NakshaFeatureBuilder withCreatedAt(long createdAt) {
    feature.getProperties().getXyz().put(CREATED_AT, createdAt);
    return this;
  }

  NakshaFeatureBuilder withUpdatedAt(Int64 updatedAt) {
    feature.getProperties().getXyz().put(UPDATED_AT, updatedAt);
    return this;
  }

  NakshaFeatureBuilder withUpdatedAt(long updatedAt) {
    feature.getProperties().getXyz().put(UPDATED_AT, updatedAt);
    return this;
  }

  @SuppressWarnings({"unchecked","rawtypes"})
  NakshaFeatureBuilder withCustomProperties(@NotNull Map properties) {
    feature.getProperties().putAll(properties);
    return this;
  }
}