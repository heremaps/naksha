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
package com.here.naksha.lib.psql;

import static com.here.naksha.lib.core.exceptions.UncheckedException.unchecked;

import com.here.naksha.lib.core.NakshaVersion;
import com.here.naksha.lib.core.models.storage.CodecError;
import com.here.naksha.lib.core.models.storage.FeatureCodec;
import com.here.naksha.lib.core.models.storage.FeatureCodecFactory;
import com.here.naksha.lib.core.models.storage.ForwardCursor;
import com.here.naksha.lib.core.util.json.Json;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple in-memory result cursor which iterates through list of {@link FeatureCodec},
 * provided as argument. It is Forward-Only cursor and not a thread-safe.
 *
 * @param <FEATURE> The feature type that the cursor returns.
 * @param <CODEC>   The codec type.
 */
@ApiStatus.AvailableSince(NakshaVersion.v2_0_11)
public class InMemoryForwardCursor<FEATURE, CODEC extends FeatureCodec<FEATURE, CODEC>>
    extends ForwardCursor<FEATURE, CODEC> {

  private static final Logger log = LoggerFactory.getLogger(InMemoryForwardCursor.class);

  private final @NotNull List<CODEC> featureCodecList;
  private final int totalFeatures;
  private int featureIdx;

  @ApiStatus.AvailableSince(NakshaVersion.v2_0_11)
  public InMemoryForwardCursor(
      @NotNull FeatureCodecFactory<FEATURE, CODEC> codecFactory, @Nullable List<CODEC> featureCodecList) {
    super(codecFactory);
    this.featureCodecList = (featureCodecList == null) ? new ArrayList<>() : featureCodecList;
    this.totalFeatures = this.featureCodecList.size();
    this.featureIdx = 0;
  }

  @Override
  @ApiStatus.AvailableSince(NakshaVersion.v2_0_11)
  protected boolean loadNextRow(@NotNull Row row) {
    if (featureIdx >= totalFeatures) {
      row.clear();
      return false;
    }
    FeatureCodec<FEATURE, CODEC> codec = this.featureCodecList.get(featureIdx);
    row.codec.setOp(codec.getOp());
    row.codec.setId(codec.getId());
    row.codec.setUuid(codec.getUuid());
    row.codec.setFeatureType(codec.getFeatureType());
    row.codec.setPropertiesType(codec.getPropertiesType());
    row.codec.setJson(codec.getJson()); // TODO : avoid deserialization (until getJson() is called)
    row.codec.setWkb(codec.getWkb()); // TODO : avoid deserialization (until getWkb() is called)
    row.codec.setRawError(codecErrorToJson(codec.getError()));
    row.codec.setErr(codec.getError());
    row.valid = true;

    this.featureIdx++;
    return true;
  }

  @Override
  @ApiStatus.AvailableSince(NakshaVersion.v2_0_11)
  public void close() {
    featureIdx = totalFeatures;
  }

  private @Nullable String codecErrorToJson(final @Nullable CodecError codecError) {
    if (codecError == null) return null;
    try (final Json jp = Json.get()) {
      return jp.writer().writeValueAsString(codecError);
    } catch (IOException e) {
      throw unchecked(e);
    }
  }
}
