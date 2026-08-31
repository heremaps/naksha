package com.here.naksha.handler.activitylog;

import java.util.Random;
import naksha.base.Action;
import naksha.base.Guid;
import naksha.base.TupleNumber;
import naksha.base.Version;

public final class GuidUtil {
  private static final Random random = new Random();

  private GuidUtil(){}

  public static Version randomVersion() {
    return Version.now(random.nextLong() & Version.SEQ_MAX, Action.VERSION);
  }

  public static Guid guid(String featureId, Version version) {
    return new Guid(featureId, new TupleNumber(
        0L, 0, 0, 0L, version.number
    ));
  }
}
