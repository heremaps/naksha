package com.here.naksha.lib.core.models.indexing;

import naksha.base.MapProxy;
import naksha.base.PlatformType;

import static naksha.base.NakshaBaseKt.String_TYPE;
import static naksha.base.Platform.forClass;

public class ConstraintMap extends MapProxy<String, Constraint> {
  public static final PlatformType<ConstraintMap> TYPE = forClass(ConstraintMap.class);

  public ConstraintMap() {
    super(String_TYPE, Constraint.TYPE);
  }
}
