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
package com.here.naksha.test.common;
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

import static com.here.naksha.lib.core.exceptions.UncheckedException.unchecked;

import naksha.base.FromJsonOptions;
import naksha.base.Platform;
import naksha.base.ToJsonOptions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;

public class JsonUtil {

  private JsonUtil() {}

  public static <T> T parseJson(final @NotNull String jsonStr, final @NotNull Class<T> type) {
    T obj = null;
    try {
      obj = type.cast(Platform.fromJSON(jsonStr, FromJsonOptions.DEFAULT));
    } catch (Exception ex) {
      Assertions.fail("Unable tor parse jsonStr " + jsonStr, ex);
      return null;
    }
    return obj;
  }

  public static String toJson(final @NotNull Object obj) {
    String jsonStr = null;
    try {
      jsonStr = Platform.toJSON(obj, ToJsonOptions.DEFAULT);
    } catch (Exception ex) {
      throw unchecked(ex);
    }
    return jsonStr;
  }
}
