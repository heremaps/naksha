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
package com.here.naksha.lib.core.models.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.here.naksha.lib.core.util.json.Json;
import com.here.naksha.lib.core.util.json.JsonUtil;
import com.here.naksha.lib.jbon.JbDictManager;
import com.here.naksha.lib.jbon.JbFeature;
import com.here.naksha.lib.jbon.JbMap;
import java.util.Map;

import com.here.naksha.lib.nak.Flags;
import org.jetbrains.annotations.NotNull;

class StringCodec extends FeatureCodec<String, StringCodec> {

  @Override
  protected Flags getDefaultFlags() {
    return new Flags();
  }

  @Override
  public @NotNull StringCodec decodeParts(boolean force) {
    featureBytes = JsonUtil.jsonToJbonByte(feature);
    return this;
  }

  @Override
  public @NotNull StringCodec encodeFeature(boolean force) {
    JbFeature jbFeature = new JbFeature(new JbDictManager()).mapBytes(featureBytes, 0, featureBytes.length);
    Map<String, Object> featureAsMap = (Map<String, Object>) new JbMap().mapReader(jbFeature.getReader()).toIMap();
    try {
      feature = Json.get().writer().writeValueAsString(featureAsMap);
    } catch (JsonProcessingException e) {
    }
    return this;
  }
}
