package com.here.naksha.lib.core.models.naksha;

import java.util.Arrays;
import naksha.base.JvmListProxy;

public final class CustomIndexMappingList extends JvmListProxy<CustomIndexMapping> {
  public CustomIndexMappingList() {
    super(CustomIndexMapping.class);
  }

  public CustomIndexMappingList(CustomIndexMapping... mappings) {
    this();
    addAll(Arrays.asList(mappings));
  }
}
