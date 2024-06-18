package com.here.naksha.lib.core;

import naksha.jbon.JbSession;
import naksha.jbon.JvmEnv;

public abstract class SessionTest {

  private JvmEnv env = JvmEnv.get();

  {
    JbSession.Companion.getThreadLocal().set(new JbSession("test", env.randomString(12), "testApp", "testAuthor"));
  }
}
