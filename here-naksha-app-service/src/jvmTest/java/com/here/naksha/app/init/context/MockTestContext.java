package com.here.naksha.app.init.context;

import com.here.naksha.app.service.NakshaApp;

class MockTestContext extends TestContext {

  MockTestContext() {
    super(() -> NakshaApp.newInstance("run", "mock-test-config"));
  }
}