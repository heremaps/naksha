package com.here.naksha.app.service.http.ops;

import java.util.Set;

public class CommonPropertiesToMask {

  private CommonPropertiesToMask() {
  }

  // must be lowercased for MaskingUtil
  public static final Set<String> COMMON_PROPERTIES_TO_MASK =
      Set.of("password", "authorization");
}
