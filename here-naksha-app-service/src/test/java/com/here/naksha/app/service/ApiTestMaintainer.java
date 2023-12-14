/*
 * Copyright (C) 2017-2023 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */
package com.here.naksha.app.service;

import static com.here.naksha.app.common.TestNakshaAppInitializer.localPsqlBasedNakshaApp;

import com.here.naksha.app.common.TestNakshaAppInitializer;
import com.here.naksha.lib.psql.PsqlStorage;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class ApiTestMaintainer implements BeforeAllCallback, ExtensionContext.Store.CloseableResource {

  private static final AtomicReference<NakshaApp> initializedNaksha = new AtomicReference<>(null);

  @Override
  public void beforeAll(ExtensionContext context) {
    if (initializedNaksha.get() == null) {
      prepareNaksha();
    }
  }

  @Override
  public void close() {
    System.out.println("CLOSING NAKSHA");
    NakshaApp app = initializedNaksha.get();
    if (app != null) {
      app.stopInstance();
    }
  }

  public static NakshaApp nakshaApp() {
    return initializedNaksha.get();
  }

  private static void prepareNaksha() {
    System.out.println("PREPARING NAKSHA");
    TestNakshaAppInitializer nakshaAppInitializer =
        localPsqlBasedNakshaApp(); // to use mock, call NakshaAppInitializer.mockedNakshaApp()
    cleanUpDb(nakshaAppInitializer.testDbUrl);
    NakshaApp app = nakshaAppInitializer.initNaksha();
    app.start();
    try {
      Thread.sleep(5000); // wait for server to come up
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    initializedNaksha.set(app);
  }

  private static void cleanUpDb(String testUrl) {
    if (testUrl != null && !testUrl.isBlank()) {
      try (PsqlStorage psqlStorage = new PsqlStorage(testUrl)) {
        psqlStorage.dropSchema();
      }
    }
  }
}
