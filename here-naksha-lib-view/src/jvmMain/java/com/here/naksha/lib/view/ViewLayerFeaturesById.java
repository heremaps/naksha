package com.here.naksha.lib.view;

import naksha.base.Id;

import java.util.HashMap;

/**
 * A map to group all variants of a feature returned from multiple layers of a view.
 *
 * <p>When a view loads features from a stack of layers, the feature can appear in each layer of the stack or only in one. This map stores all found feature, grouped by the feature {@link Id}.
 * @since 3.0
 */
public class ViewLayerFeaturesById extends HashMap<Id, ViewLayerFeatureStack> {
}
