package com.here.naksha.lib.core.models.indexing;

import naksha.base.MapProxy;
import naksha.base.PlatformType;

import static naksha.base.NakshaBaseKt.String_TYPE;
import static naksha.base.Platform.forClass;

public class IndexMap extends MapProxy<String, Index> {
  public static final PlatformType<IndexMap> TYPE = forClass(IndexMap.class);

  public IndexMap() {
    super(String_TYPE, Index.TYPE);
  }
}
