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
package com.here.naksha.lib.view;

import naksha.base.Platform;
import naksha.model.IStorage;
import naksha.model.Naksha;
import naksha.model.NakshaContext;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaMap;
import naksha.model.objects.NakshaStorage;
import naksha.model.request.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static naksha.base.Platform.javaProxy;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Base class for all PostgresQL tests that require some test database.
 */
@SuppressWarnings("unused")
@TestMethodOrder(OrderAnnotation.class)
abstract class PsqlTests {

  static final Logger log = LoggerFactory.getLogger(PsqlTests.class);

  /**
   * Prevents that the test drops the schema at the start.
   */
  static final boolean DROP_INITIALLY = true;

  /**
   * If the test drop the database at the end (false by default, to verify results).
   */
  static final boolean DROP_FINALLY = false;

  abstract boolean enabled();

  final boolean runTest() {
    return enabled();
  }

  public static final String TEST_MAP_ID = "lib_view_test";
  public static final String TEST_APP_ID = "test_app";
  public static final String TEST_AUTHOR = "test_author";
  static IStorage storage;
  static NakshaMap map;
  static @Nullable NakshaContext nakshaContext;

  protected static @NotNull SuccessResponse assertSuccess(@NotNull Response response) {
    if (response instanceof ErrorResponse) {
      ((ErrorResponse)response).getError().print(Platform.getLogger());
    }
    return assertInstanceOf(SuccessResponse.class, response);
  }

  protected static @NotNull SuccessResponse executeWrite(@NotNull WriteRequest request) {
    return executeWrite(request, null);
  }
  protected static @NotNull SuccessResponse executeWrite(
        @NotNull WriteRequest request,
        @Nullable SessionOptions sessionOptions
  ) {
    return storage.useWriteSession(sessionOptions, session -> {
      final @NotNull SuccessResponse response = assertSuccess(session.execute(request));
      session.commit();
      return response;
    });
  }

  @BeforeAll
  static void beforeTest() {
    nakshaContext = NakshaContext.currentContext().withAppId(TEST_APP_ID).withAuthor(TEST_AUTHOR);
    storage = Naksha.useStorage(
      NakshaStorage.fromJSON(
        "{\"id\":\"local_psql_test_storage\",\"className\":\"naksha.psql.PsqlTestStorage\"}"
      )
    );
    assertNotNull(storage);

    // Drop the map, if it exists
    executeWrite(new WriteRequest().add(new Write().deleteMapById(TEST_MAP_ID)));

    // Create the map.
    SuccessResponse response = executeWrite(new WriteRequest().add(new Write().createMap(new NakshaMap(TEST_MAP_ID))));
    assertEquals(1, response.getFeatures().size());
    NakshaFeature raw = response.getFeatures().get(0);
    assertNotNull(raw);
    map = javaProxy(raw, NakshaMap.class);
  }
}
