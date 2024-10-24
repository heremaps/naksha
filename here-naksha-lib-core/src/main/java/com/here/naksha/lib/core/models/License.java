/*
 * Copyright (C) 2017-2024 HERE Europe B.V.
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
package com.here.naksha.lib.core.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.List;

public class License {

  // Source: https://github.com/shinnn/spdx-license-ids/blob/master/index.json
  // Information about the licenses can be found here: https://spdx.org/licenses/ OR
  // https://choosealicense.com/licenses/<keyword>
  private static List<String> allowedKeywords = Arrays.asList(
      "AFL-3.0",
      "Apache-2.0",
      "Artistic-2.0",
      "BSL-1.0",
      "BSD-2-Clause",
      "BSD-3-Clause",
      "BSD-3-Clause-Clear",
      "CC0-1.0",
      "CC-BY-4.0",
      "CC-BY-SA-4.0",
      "WTFPL",
      "ECL-1.0",
      "ECL-2.0",
      "EUPL-1.1",
      "AGPL-3.0-only",
      "GPL-2.0-only",
      "GPL-3.0-only",
      "LGPL-2.1-only",
      "LGPL-3.0-only",
      "ISC",
      "LPPL-1.3c",
      "MS-PL",
      "MIT",
      "MPL-2.0",
      "OSL-3.0",
      "PostgreSQL",
      "OFL-1.1",
      "NCSA",
      "Unlicense",
      "Zlib",
      "ODbL-1.0");

  @JsonValue
  private String keyword;

  public String getKeyword() {
    return keyword;
  }

  public void setKeyword(final String keyword) {
    this.keyword = keyword;
  }

  public License withKeyword(final String keyword) {
    setKeyword(keyword);
    return this;
  }

  @JsonCreator
  public static License forKeyword(String keyword) {
    License l = new License();
    if (!allowedKeywords.contains(keyword)) {
      throw new IllegalArgumentException("\""
          + keyword
          + "\" is not a valid license keyword. Allowed keywords are: "
          + String.join(", ", allowedKeywords));
    }
    l.keyword = keyword;
    return l;
  }
}
