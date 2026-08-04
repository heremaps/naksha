package com.here.naksha.lib.view;

import naksha.base.Id;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

/**
 * A map between {@link ViewLayer} and all {@link Id}'s missing in this layer.
 * @since 3.0
 */
public class MissingIdsByLayer extends HashMap<ViewLayer, MissingIds> {

  /**
   * Returns the missing {@link Id}'s list for the given layer.
   * @param layer the layer for which to return the missing {@link Id}'s list.
   * @return the missing {@link Id}'s list, if no such entry exists, a new one is created.
   * @since 3.0
   */
  public @NotNull MissingIds getOrCreate(final ViewLayer layer) {
    return this.computeIfAbsent(layer, MissingIds::new);
  }
}
