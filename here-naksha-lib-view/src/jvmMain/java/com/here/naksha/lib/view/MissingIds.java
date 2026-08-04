package com.here.naksha.lib.view;

import naksha.base.Id;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * A list of feature {@link Id}'s that are missing from a specific layer.
 * @since 3.0
 */
public class MissingIds extends ArrayList<@NotNull Id> {
  /**
   * Create a new list of missing {@link Id}'s.
   * @param viewLayer the layer in which the {@link Id}'s are missing.
   */
  public MissingIds(@NotNull ViewLayer viewLayer) {
    this.viewLayer = viewLayer;
  }

  private final @NotNull ViewLayer viewLayer;

  /**
   * The layer from which the {@link Id}'s are missing.
   */
  public @NotNull ViewLayer getViewLayer() {
    return viewLayer;
  }

  public void addIfAbsent(@NotNull Id id) {
    if (this.contains(id)) return;
    this.add(id);
  }

  public void addAllIfAbsent(@Nullable MissingIds ids) {
    if (ids == null) return;
    for (Id id : ids) {
      if (this.contains(id)) return;
      this.add(id);
    }
  }
}
