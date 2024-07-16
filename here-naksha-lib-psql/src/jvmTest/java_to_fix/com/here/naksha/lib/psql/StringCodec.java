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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.here.naksha.lib.core.models.storage.FeatureCodec;
import com.here.naksha.lib.core.util.json.Json;
import naksha.jbon.JbDictManager;
import naksha.jbon.JbFeatureDecoder;
import naksha.jbon.JbMapDecoder;
import com.here.naksha.lib.nak.Flags;
import com.here.naksha.lib.nak.GZip;
import java.util.Map;
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
    byte[] rawFeatureBytes = flags.isFeatureEncodedWithGZip() ? GZip.INSTANCE.gunzip(featureBytes) : featureBytes;
    JbFeature jbFeature = new JbFeature(new JbDictManager()).mapBytes(rawFeatureBytes, 0, rawFeatureBytes.length);
    Map<String, Object> featureAsMap = (Map<String, Object>)
        new JbMap().mapReader(jbFeature.getReader()).toIMap();
    try {
      feature = Json.get().writer().writeValueAsString(featureAsMap);
    } catch (JsonProcessingException e) {
    }
    return this;
  }
}
