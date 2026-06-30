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
    // Version uses only the lower 56 bits; the upper 8 bits must be 0 so that
    // the value survives the toString()/fromString() round-trip intact.
    return new Version(random.nextLong() & 0x00FF_FFFF_FFFF_FFFFL);
  }

  public static Guid guid(String featureId, Version version) {
    return new Guid(featureId, new TupleNumber(
        new JvmInt64(0), 0, 0, new JvmInt64(0), version.number
    ));
  }
}
