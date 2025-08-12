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
package com.here.naksha.app.service.models;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import naksha.base.*;
import org.jetbrains.annotations.NotNull;

import static naksha.base.Platform.forClass;

public class IterateHandle extends AnyObject {
  public static final PlatformType<IterateHandle> TYPE = forClass(IterateHandle.class);
  private static final String OFFSET_KEY = "offset";
  private static final String LIMIT_KEY = "limit";

  @Override
  public void onCreation() {
    setOffset(0);
    setLimit(0);
  }

  public int getOffset() {
    return getOr(OFFSET_KEY, 0);
  }

  public void setOffset(int offset) {
    set(OFFSET_KEY, offset);
  }

  public IterateHandle withOffset(int offset) {
    setOffset(offset);
    return this;
  }

  public int getLimit() {
    return getOr(LIMIT_KEY, Integer.MAX_VALUE);
  }

  public void setLimit(int limit) {
    set(LIMIT_KEY, limit);
  }

  public IterateHandle withLimit(int limit) {
    setLimit(limit);
    return this;
  }

  public String base64EncodedSerializedJson() {
    return Base64.getEncoder().encodeToString(Platform.toJson(this, ToJsonOptions.DEFAULT).getBytes(StandardCharsets.UTF_8));
  }

  public static IterateHandle base64DecodedDeserializedJson(final @NotNull String handle) {
    final String json = new String(Base64.getDecoder().decode(handle));
    return Platform.fromJson(json, IterateHandle.TYPE);
  }
}
