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
package com.here.naksha.lib.psql;

import naksha.jbon.IMap;
import naksha.jbon.JbSession;
import naksha.jbon.JvmEnv;

public class JsonUtil {

  public static byte[] jsonToJbonByte(String json) {
    if (json == null) {
      return null;
    }
    Object feature = JvmEnv.get().parse(json);
    return JbSession.Companion.get().newBuilder(null, 65536).buildFeatureFromMap((IMap) feature);
  }
}
