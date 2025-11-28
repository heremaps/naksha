package com.here.naksha.app.service.http.tasks.processor;

import java.util.List;
import naksha.model.XyzNs;
import naksha.model.objects.NakshaFeature;
import org.jetbrains.annotations.Nullable;

public final class TagsPreProcessor implements FeaturePreProcessor<NakshaFeature> {

  private static final boolean DO_NORMALIZE = true;

  private final List<String> tagsToRemove;
  private final List<String> tagsToAdd;

  public TagsPreProcessor(@Nullable List<String> tagsToRemove, @Nullable List<String> tagsToAdd) {
    this.tagsToRemove = tagsToRemove;
    this.tagsToAdd = tagsToAdd;
  }

  @Override
  public NakshaFeature preProcess(NakshaFeature feature) {
    XyzNs xyzNs = feature.getProperties().getXyz();
    if (hasItems(tagsToAdd)) {
      xyzNs.addTags(tagsToAdd, DO_NORMALIZE);
    }
    if (hasItems(tagsToRemove)) {
      xyzNs.removeTags(tagsToRemove, DO_NORMALIZE);
    }
    return feature;
  }

  private static boolean hasItems(List<String> tags) {
    return tags != null && tags.size() > 0;
  }
}