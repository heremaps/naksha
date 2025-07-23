package com.here.naksha.handler.activitylog;

import java.util.Map;
import java.util.Objects;
import naksha.model.Version;
import naksha.model.request.FeatureTuple;
import naksha.model.request.ResultFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class MaxVersionResultFilter implements ResultFilter {

  private final Map<String, Version> maxVersionsByFeatureId;

  MaxVersionResultFilter(Map<String, Version> maxVersionsByFeatureId) {
    this.maxVersionsByFeatureId = maxVersionsByFeatureId;
  }

  @Override
  public @Nullable FeatureTuple filter(@NotNull FeatureTuple featureTuple) {
    Version maxVersion = maxVersionsByFeatureId.get(featureTuple.getId());
    if(maxVersion == null) {
      // no restriction -> include the tuple in result
      return featureTuple;
    } else if(maxVersion.compareTo(featureTuple.tupleNumber.version) >= 0){
      // tuple version <= maxVersion —> include
      return featureTuple;
    } else {
      // tuple version > maxVersion —> exclude
      return null;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    MaxVersionResultFilter that = (MaxVersionResultFilter) o;
    return Objects.equals(maxVersionsByFeatureId, that.maxVersionsByFeatureId);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(maxVersionsByFeatureId);
  }
}
