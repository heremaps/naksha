package com.here.naksha.handler.activitylog;

import java.util.Random;
import naksha.base.JvmInt64;
import naksha.model.Guid;
import naksha.model.TupleNumber;
import naksha.model.Version;

public final class GuidUtil {
  private static final Random random = new Random();

  private GuidUtil(){}

  public static Version randomVersion() {
    return new Version(random.nextLong());
  }

  public static Guid guid(String featureId, Version version) {
    return new Guid(featureId, new TupleNumber(
        new JvmInt64(0), 0, 0, new JvmInt64(0), version
    ));
  }
}
