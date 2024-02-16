package com.here.naksha.lib.psql;

import com.here.naksha.lib.jbon.JbSession;
import com.here.naksha.lib.jbon.JvmEnv;

public abstract class SessionTest {

  private JvmEnv env = JvmEnv.get();

  {
    JbSession.Companion.getThreadLocal().set(new JbSession("test", env.randomString(12), "testApp", "testAuthor"));
  }
}
