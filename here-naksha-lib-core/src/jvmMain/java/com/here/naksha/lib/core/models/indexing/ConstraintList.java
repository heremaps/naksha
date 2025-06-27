package com.here.naksha.lib.core.models.indexing;

import naksha.base.ListProxy;
import naksha.base.PlatformType;

import static naksha.base.Platform.forClass;

public class ConstraintList extends ListProxy<Constraint> {
  public static final PlatformType<ConstraintList> TYPE = forClass(ConstraintList.class);

  public ConstraintList() {
    super(Constraint.TYPE);
  }
}
