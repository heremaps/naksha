package com.here.naksha.lib.core.models.indexing;

import naksha.base.ListProxy;
import naksha.base.PlatformType;

import static naksha.base.Platform.forClass;

public class IndexPropertyList extends ListProxy<IndexProperty> {
  public static final PlatformType<IndexPropertyList> TYPE = forClass(IndexPropertyList.class);

  public IndexPropertyList() {
    super(IndexProperty.TYPE);
  }
}
