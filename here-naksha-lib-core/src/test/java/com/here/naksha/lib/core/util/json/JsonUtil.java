package com.here.naksha.lib.core.util.json;

import com.here.naksha.lib.jbon.IMap;
import com.here.naksha.lib.jbon.JbSession;
import com.here.naksha.lib.jbon.JvmEnv;

public class JsonUtil {

  public static byte[] jsonToJbonByte(String json) {
    if (json == null) {
      return null;
    }
    Object feature = JvmEnv.get().parse(json);
    return JbSession.Companion.get().newBuilder(null, 65536).buildFeatureFromMap((IMap) feature);
  }
}
