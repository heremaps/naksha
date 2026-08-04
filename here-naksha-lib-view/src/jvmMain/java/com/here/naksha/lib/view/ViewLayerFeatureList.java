package com.here.naksha.lib.view;

import naksha.base.Id;

import java.util.ArrayList;

/**
 * A list of {@link ViewLayerFeature} <i>(a thin wrapper around a {@link naksha.model.objects.NakshaFeature NakshaFeature})</i>, with different {@link Id}'s, form different one or multiple {@link ViewLayer}'s.
 * @since 3.0
 */
public class ViewLayerFeatureList extends ArrayList<ViewLayerFeature> {
  /** Creates an empty list. */
  public ViewLayerFeatureList() {}

  /** Creates an empty list with a specific initial capacity. */
  public ViewLayerFeatureList(int capacity) {
    super(capacity);
  }
}
