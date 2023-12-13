package com.here.naksha.lib.view;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ViewCollection {

  private final String name;
  private final List<ViewLayer> layers;

  public ViewCollection(String name, List<ViewLayer> layers) {
    this.name = name;
    this.layers = Collections.unmodifiableList(layers);
  }

  public ViewCollection(String name, ViewLayer... orderedLowerLevelStorages) {
    this.name = name;

    List<ViewLayer> tempLayers = new ArrayList<>();
    tempLayers.addAll(Arrays.asList(orderedLowerLevelStorages));
    this.layers = Collections.unmodifiableList(tempLayers);
  }

  public String getName() {
    return name;
  }

  public List<ViewLayer> getLayers() {
    return layers;
  }
}
