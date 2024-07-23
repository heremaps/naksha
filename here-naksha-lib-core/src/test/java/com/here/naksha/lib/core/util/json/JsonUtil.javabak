package com.here.naksha.lib.core.util.json;

import naksha.jbon.IMap;
import naksha.jbon.JbSession;
import naksha.jbon.JvmEnv;

public class JsonUtil {

  public static byte[] jsonToJbonByte(String json) {
    if (json == null) {
      return null;
    }
    Object feature = JvmEnv.get().parse(json);
    return JbSession.Companion.get().newBuilder(null, 65536).buildFeatureFromMap((IMap) feature);
  }
}
