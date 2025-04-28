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
package com.here.naksha.app.common;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for all API-related tests. Extending this class ensures that NakshaApp & all required storages are running
 */
@ExtendWith({ApiTestMaintainer.class})
public abstract class ApiTest {

  private static final AtomicBoolean storageInitialized = new AtomicBoolean(false);
  private static final Logger logger = LoggerFactory.getLogger(ApiTest.class);

  private final NakshaTestWebClient nakshaClient;

  public ApiTest() {
    this(new NakshaTestWebClient());
  }

  public ApiTest(NakshaTestWebClient nakshaClient) {
    this.nakshaClient = nakshaClient;
  }

  public NakshaTestWebClient getNakshaClient() {
    return nakshaClient;
  }

  @BeforeAll
  public static void setupStorage(){
    if(storageInitialized.compareAndSet(false, true)){
      logger.info("Common storage not yet set, delegating initialization...");
      CommonApiTestSetup.setupCommonStorage(new NakshaTestWebClient());
    }
  }
}
