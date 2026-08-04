package com.here.naksha.lib.view;

import naksha.base.Id;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * An unordered list of {@link ViewLayerFeature} with the same {@link Id}.
 *
 * <h3>Notes</h3>
 * <p>The class does not verify that all features added really share the same {@link Id}, this is just a contract. However, this verification could be added.
 * @since 3.0
 */
public class ViewLayerFeatureStack extends ArrayList<@NotNull ViewLayerFeature> {
  /**
   * Create a new list of features with the same {@link Id}, but coming from different {@link ViewLayer}.
   * @param id the identifier of all features.
   */
  public ViewLayerFeatureStack(@NotNull Id id) {
    this.id = id;
  }

  private final @NotNull Id id;

  /**
   * The {@link Id} of all features being in this list.
   */
  public @NotNull Id getId() {
    return id;
  }

}
