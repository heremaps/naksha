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
package com.here.naksha.lib.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Assertions;

public class TestFileLoader {

  private static final String TEST_DATA_FOLDER = "src/test/resources/unit_test_data/";

  private TestFileLoader() {}

  public static String loadFileOrFail(String fileName) {
    try {
      return new String(Files.readAllBytes(Paths.get(TEST_DATA_FOLDER + fileName)));
    } catch (IOException e) {
      Assertions.fail("Unable tor read test file " + fileName, e);
      return null;
    }
  }
}
